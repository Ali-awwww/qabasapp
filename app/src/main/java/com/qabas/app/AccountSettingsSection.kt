package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

/**
 * Secure Account Settings — no hardcoded passwords.
 * Password is stored only as salted SHA-256 hash in SharedPreferences.
 */
@Composable
fun AccountSettingsSection(context: Context) {
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
    var isAdmin by remember { mutableStateOf(prefs.getBoolean("is_admin", false)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "بيانات حساب المطور الرئيسي",
            color = GoldPrimary,
            fontFamily = TajawalFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Surface(
            color = Color(0xFFEF4444).copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "تنبيه أمني",
                        color = Color(0xFFEF4444),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "لا تُخزَّن كلمات المرور في الكود المصدري ولا تُعرض في الواجهة. غيّر كلمة المرور من هنا فقط عند الحاجة، ويفضّل الاعتماد على Firebase Authentication للحساب الحقيقي.",
                        color = Color(0xFFFECACA),
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني للمطور", color = Color.Gray, fontFamily = CairoFont) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Text(
            "تغيير كلمة المرور (اختياري)",
            color = GoldPrimary,
            fontFamily = TajawalFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            "اترك الحقول فارغة إذا لم ترد تغيير كلمة المرور. الحد الأدنى 8 أحرف.",
            color = Color.Gray,
            fontFamily = NotoSansFont,
            fontSize = 12.sp
        )

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("كلمة المرور الجديدة", color = Color.Gray, fontFamily = CairoFont) },
            visualTransformation = if (showNewPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showNewPassword = !showNewPassword }) {
                    Icon(
                        if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("تأكيد كلمة المرور الجديدة", color = Color.Gray, fontFamily = CairoFont) },
            visualTransformation = if (showConfirmPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                    Icon(
                        if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )

        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

        Text("الأمان والحماية", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("المصادقة الثنائية (2FA)", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("تفضيل محلي لتذكير المطوّر بتفعيل 2FA على حساب Firebase/البريد.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            Switch(
                checked = is2FAEnabled,
                onCheckedChange = {
                    is2FAEnabled = it
                    prefs.edit().putBoolean("dev_2fa_enabled", it).apply()
                },
                colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldSecondary)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("إشعارات المبيعات الجديدة", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("تلقي إشعار فور حدوث عملية شراء جديدة داخل التطبيق.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            Switch(
                checked = isTransactionNotificationsEnabled,
                onCheckedChange = {
                    isTransactionNotificationsEnabled = it
                    prefs.edit().putBoolean("dev_transaction_notifications", it).apply()
                },
                colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldSecondary)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("الرتبة الإدارية", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("تحديد ما إذا كان الحساب الحالي لديه صلاحيات إدارية.", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            Switch(
                checked = isAdmin,
                onCheckedChange = {
                    isAdmin = it
                    prefs.edit().putBoolean("is_admin", it).apply()
                },
                colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldSecondary)
            )
        }

        // Sync with Firebase custom claim
        val syncCtx = LocalContext.current
        val syncScope = rememberCoroutineScope()
        Button(
            onClick = {
                syncScope.launch {
                    try {
                        CloudServices.syncAdminClaimFromFirebase(syncCtx)
                        // Refresh local state after sync
                        isAdmin = prefs.getBoolean("is_admin", false)
                    } catch (e: Exception) {
                        Toast.makeText(syncCtx, "فشل المزامنة: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
        ) {
            Text("تنزيل من فايربيس", color = Color.Black, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val emailTrim = email.trim()
                if (emailTrim.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches()) {
                    Toast.makeText(context, "صيغة البريد الإلكتروني غير صحيحة", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val wantsPasswordChange = newPassword.isNotEmpty() || confirmPassword.isNotEmpty()
                if (wantsPasswordChange) {
                    when {
                        newPassword.length < 8 -> {
                            Toast.makeText(context, "كلمة المرور يجب أن تكون 8 أحرف على الأقل", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        newPassword != confirmPassword -> {
                            Toast.makeText(context, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show()
                            return@Button
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
                    if (wantsPasswordChange) "تم حفظ البريد وتحديث تجزئة كلمة المرور محلياً" else "تم حفظ بيانات حساب المطور",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
        ) {
            Text("حفظ التغييرات", color = Color.Black, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
