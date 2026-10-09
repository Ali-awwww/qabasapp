package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.TestTags
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch

/**
 * شاشة تسجيل الدخول والمصادقة الأمنية بتصميم Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 * تدعم تسجيل الدخول بالبريد والرمز، أو المصادقة بنقرة واحدة عبر Google Credential Manager و Supabase Auth.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (Boolean) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToRules: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var accountInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var showGoogleSetupDialog by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    // Pre-fill last saved email or custom identifier if available
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val savedAccount = prefs.getString("saved_login_email", "")?.takeIf { it.isNotBlank() }
            ?: prefs.getString("user_custom_id", "") ?: ""
        if (savedAccount.isNotBlank()) {
            accountInput = savedAccount
        }
    }

    // الدالة المركزية لتسجيل الدخول عبر Google
    val handleGoogleSignIn: () -> Unit = {
        val webClientId = GoogleAuthHelper.getWebClientId(context)
        if (webClientId.isBlank() || webClientId.contains("your_")) {
            Toast.makeText(context, "الرجاء إدخال معرّف Google Web Client ID أولاً لتفعيل الدخول السحابي", Toast.LENGTH_LONG).show()
            showGoogleSetupDialog = true
        } else {
            isGoogleLoading = true
            coroutineScope.launch {
                val result = GoogleAuthHelper.signInWithGoogle(context, webClientId)
                result.onSuccess { googleResult ->
                    val lowerEmail = googleResult.email.lowercase(java.util.Locale.ROOT)
                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    
                    // محاولة تسجيل الدخول في Supabase أولاً لضمان مزامنة الجلسة
                    var supabaseSuccess = false
                    if (SupabaseServices.isSupabaseAvailable) {
                        supabaseSuccess = SupabaseServices.Auth.loginWithGoogle(googleResult.idToken)
                    }

                    if (SupabaseServices.isSupabaseAvailable && !supabaseSuccess) {
                        isGoogleLoading = false
                        Toast.makeText(context, "فشل الاتصال بسحابة قبس (Supabase). تأكد من إعدادات Google Auth في لوحة التحكم.", Toast.LENGTH_LONG).show()
                        showGoogleSetupDialog = true
                        return@launch
                    }

                    val isAdmin = prefs.getBoolean("is_admin", false) || CloudServices.isOwnerAccount(lowerEmail)
                    val isRelative = prefs.getBoolean("is_relative", false)

                    // إنشاء أو استعادة المعرّف الخاص للمستخدم ورقم الحساب الرسمي
                    val customId = UserIdentifierManager.getOrCreateCustomId(context, lowerEmail, googleResult.displayName)
                    val accountNumber = UserIdentifierManager.getOrCreateAccountNumber(context, lowerEmail, googleResult.displayName)

                    val externalId = SupabaseServices.Auth.getCurrentUserId() ?: lowerEmail
                    var userType = if (isAdmin) "مطور" else "مجاني"
                    
                    if (SupabaseServices.isSupabaseAvailable) {
                        val existing = SupabaseServices.Database.ensureUser(externalId, lowerEmail, googleResult.displayName, customId, accountNumber)
                        if (existing != null && existing.type.isNotBlank()) userType = existing.type
                        SupabaseServices.Database.saveUser(
                            externalId,
                            googleResult.displayName ?: lowerEmail.substringBefore('@'),
                            lowerEmail,
                            userType,
                            customId,
                            accountNumber
                        )
                    }

                    val finalIsAdmin = isAdmin || userType == "مطور" || userType == "خاص"
                    val finalIsDeveloper = CloudServices.isOwnerAccount(lowerEmail) || userType == "مطور"

                    val effectiveGoogleName = googleResult.displayName ?: googleResult.email.substringBefore('@')
                    val nameWithAccount = UserIdentifierManager.formatDisplayNameWithAccount(effectiveGoogleName, accountNumber)

                    LocalAccountRegistry.registerOrUpdateAccount(
                        context = context,
                        account = LocalAccountRegistry.AccountRecord(
                            id = googleResult.email,
                            name = effectiveGoogleName,
                            email = googleResult.email,
                            customId = customId,
                            accountNumber = accountNumber,
                            type = if (finalIsDeveloper) "مطور" else "مجاني",
                            status = "active"
                        )
                    )

                    prefs.edit().apply {
                        putBoolean("is_logged_in", true)
                        putBoolean("is_guest", false)
                        putBoolean("is_admin", finalIsAdmin)
                        putBoolean("is_developer", finalIsDeveloper)
                        putBoolean("is_premium", true)
                        putBoolean("is_relative", isRelative)
                        putString("user_email", googleResult.email)
                        putString("user_name", effectiveGoogleName)
                        putString("user_display_name", effectiveGoogleName)
                        putString("user_custom_id", customId)
                        putString("user_tag", UserIdentifierManager.formatDisplay(customId))
                        putString("user_account_number", accountNumber)
                        putString("user_name_with_account", nameWithAccount)
                        if (!googleResult.displayName.isNullOrBlank()) {
                            putString("user_name", googleResult.displayName)
                        }
                        if (rememberMe) {
                            putString("saved_login_email", googleResult.email)
                        }
                        apply()
                    }

                    SupabaseRepository.getInstance().emitLocalAuthenticated(
                        email = googleResult.email,
                        name = googleResult.displayName,
                        isAdmin = finalIsAdmin
                    )

                    CloudServices.syncAdminClaim(context)
                    val firstUserBadge = if (UserIdentifierManager.isFirstAccount(accountNumber)) " (أول حساب مسجل 👑)" else ""
                    Toast.makeText(
                        context,
                        "مرحباً بك ${googleResult.displayName ?: googleResult.email}! رقم حسابك: $accountNumber$firstUserBadge 🚀",
                        Toast.LENGTH_SHORT
                    ).show()
                    onLoginSuccess(finalIsAdmin)
                }.onFailure { err ->
                    if (err is androidx.credentials.exceptions.GetCredentialCancellationException) {
                        // User cancelled
                    } else {
                        val msg = err.message ?: "خطأ أثناء محاولة تسجيل الدخول عبر Google"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        // Automatically show guide dialog if no credentials or mismatch
                        showGoogleSetupDialog = true
                    }
                }
                isGoogleLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate)
            .testTag(TestTags.LOGIN_SCREEN)
    ) {
        // 1. Background Grid HUD
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val step = 60.dp.toPx()
            for (i in 0..(w / step).toInt()) {
                drawLine(primaryColor.copy(alpha = 0.02f), Offset(i * step, 0f), Offset(i * step, h), 1f)
            }
            for (i in 0..(h / step).toInt()) {
                drawLine(primaryColor.copy(alpha = 0.02f), Offset(0f, i * step), Offset(w, i * step), 1f)
            }
        }

        // 2. Corner Decors
        HudCornerDecor(modifier = Modifier.fillMaxSize(), color = primaryColor.copy(alpha = 0.15f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Badge (Geometric HUD)
            Surface(
                color = primaryColor.copy(alpha = 0.08f),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "ACCESS PROTOCOL // SECURE AUTH HUD",
                    color = primaryColor,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Text(
                text = "بوابة الوصول الرقمية",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "استوديو قبس: فضاء المونتاج القرآني والذكاء الاصطناعي",
                color = accentColor.copy(alpha = 0.85f),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Google One-Tap SSO Section (Top Priority as requested)
            GoogleSignInButton(
                isLoading = isGoogleLoading,
                onClick = handleGoogleSignIn,
                onConfigureClick = { showGoogleSetupDialog = true }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // SSO / Password Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.15f))
                            )
                        )
                )
                Text(
                    text = "أو عبر البريد الرقمي // IDENTITY",
                    color = Color.White.copy(alpha = 0.4f),
                    fontFamily = RobotoMonoFont,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Login Glass Card
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent)),
                        shape = CutCornerShape(16.dp)
                    ),
                shape = CutCornerShape(16.dp),
                backgroundColor = CardSurface.copy(alpha = 0.7f),
                borderColor = Color.White.copy(alpha = 0.05f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Fields Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp, 2.dp).background(primaryColor))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "بيانات الهوية المحلية",
                            color = Color.White.copy(alpha = 0.9f),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Diagnostic Error Message Banner
                    if (authErrorMessage != null) {
                        Surface(
                            color = Color(0xFF3E1218),
                            border = BorderStroke(1.dp, Color(0xFFFF4D4D)),
                            shape = CutCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF4D4D), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = authErrorMessage ?: "",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Dual-Mode Field: Email (Gmail) or Custom Identifier
                    OutlinedTextField(
                        value = accountInput,
                        onValueChange = { 
                            accountInput = it
                            authErrorMessage = null
                        },
                        label = { Text("البريد الإلكتروني (Gmail) أو المعرّف الخاص", fontFamily = CairoFont, fontSize = 12.sp) },
                        placeholder = { 
                            Text(
                                "user@gmail.com أو @username أو QBS-1082", 
                                color = Color.Gray.copy(alpha = 0.5f), 
                                fontSize = 11.sp, 
                                fontFamily = CairoFont
                            ) 
                        },
                        leadingIcon = {
                            val isLikelyEmail = UserIdentifierManager.isEmail(accountInput)
                            Icon(
                                if (isLikelyEmail) Icons.Default.Email else Icons.Default.Person, 
                                contentDescription = null, 
                                tint = primaryColor.copy(alpha = 0.8f), 
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = primaryColor,
                            focusedContainerColor = Color.White.copy(alpha = 0.02f),
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = CutCornerShape(8.dp),
                        singleLine = true
                    )

                    // Cybernetic Dual-Login Hint Badge
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle, 
                            contentDescription = null, 
                            tint = primaryColor.copy(alpha = 0.7f), 
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "نظام موحد: يمكنك كتابة بريدك (Gmail) أو معرّفك الخاص مباشرة",
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp
                        )
                    }

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            authErrorMessage = null
                        },
                        label = { Text("كلمة المرور", fontFamily = CairoFont, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = primaryColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = primaryColor,
                            focusedContainerColor = Color.White.copy(alpha = 0.02f),
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = CutCornerShape(8.dp),
                        singleLine = true
                    )

                    // Remember Me
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { rememberMe = !rememberMe },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .border(1.dp, if (rememberMe) primaryColor else Color.Gray, CutCornerShape(4.dp))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (rememberMe) Box(modifier = Modifier.fillMaxSize().background(primaryColor, CutCornerShape(2.dp)))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "تذكر بيانات الدخول برمجياً",
                            color = if (rememberMe) Color.White else Color.Gray,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Submit Button (Geometric Solid)
                    Button(
                        onClick = {
                            val trimmedInput = accountInput.trim()
                            if (trimmedInput.isBlank() || password.isBlank()) {
                                authErrorMessage = "الرجاء إدخال البريد أو المعرّف الخاص وكلمة المرور"
                                Toast.makeText(context, authErrorMessage, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            authErrorMessage = null
                            isLoading = true
                            coroutineScope.launch {
                                // 1. فك تشفير المعرّف الخاص أو البريد
                                val resolvedPair = UserIdentifierManager.resolveIdentifierToEmail(trimmedInput, context)
                                if (resolvedPair == null) {
                                    val err = "تعذر العثور على حساب مرتبط بالمعرّف '$trimmedInput'. يرجى التأكد من صحة المعرّف أو تسجيل الدخول ببريدك الإلكتروني (Gmail)."
                                    authErrorMessage = err
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    isLoading = false
                                    return@launch
                                }

                                val targetEmail = resolvedPair.first
                                val detectedCustomId = resolvedPair.second
                                val lowerEmail = targetEmail.lowercase(java.util.Locale.ROOT)
                                val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                                val isAdmin = prefs.getBoolean("is_admin", false) || CloudServices.isOwnerAccount(lowerEmail)
                                val isRelative = prefs.getBoolean("is_relative", false)

                                val diagResult = SupabaseRepository.getInstance().diagnoseSignInAttempt(targetEmail, password)

                                if (diagResult.isSuccess && diagResult.hasSession) {
                                    val externalId = diagResult.userId ?: SupabaseServices.Auth.getCurrentUserId() ?: lowerEmail
                                    var userType = if (isAdmin) "مطور" else "مجاني"

                                    // حفظ أو استعادة المعرّف الخاص
                                    val finalCustomId = if (detectedCustomId.isNotBlank()) {
                                        UserIdentifierManager.saveCustomId(context, detectedCustomId, targetEmail)
                                        detectedCustomId
                                    } else {
                                        UserIdentifierManager.getOrCreateCustomId(context, targetEmail)
                                    }
                                    val accountNumber = UserIdentifierManager.getOrCreateAccountNumber(context, targetEmail)

                                    if (SupabaseServices.isSupabaseAvailable) {
                                        val existing = SupabaseServices.Database.ensureUser(externalId, lowerEmail, null, finalCustomId, accountNumber)
                                        if (existing != null && existing.type.isNotBlank()) userType = existing.type
                                        SupabaseServices.Database.saveUser(
                                            id = externalId,
                                            name = lowerEmail.substringBefore('@'),
                                            email = targetEmail,
                                            type = userType,
                                            customId = finalCustomId,
                                            accountNumber = accountNumber
                                        )
                                    }

                                    val finalIsAdmin = isAdmin || userType == "مطور" || userType == "خاص"
                                    val finalIsDeveloper = CloudServices.isOwnerAccount(lowerEmail) || userType == "مطور"

                                    val effectiveLoginName = prefs.getString("user_name", "")?.takeIf { it.isNotBlank() } ?: targetEmail.substringBefore('@')
                                    val nameWithAccount = UserIdentifierManager.formatDisplayNameWithAccount(effectiveLoginName, accountNumber)

                                    LocalAccountRegistry.registerOrUpdateAccount(
                                        context = context,
                                        account = LocalAccountRegistry.AccountRecord(
                                            id = targetEmail,
                                            name = effectiveLoginName,
                                            email = targetEmail,
                                            customId = finalCustomId,
                                            accountNumber = accountNumber,
                                            type = if (finalIsDeveloper) "مطور" else userType,
                                            status = "active"
                                        )
                                    )

                                    prefs.edit().apply {
                                        putBoolean("is_logged_in", true)
                                        putBoolean("is_guest", false)
                                        putBoolean("is_admin", finalIsAdmin)
                                        putBoolean("is_developer", finalIsDeveloper)
                                        putBoolean("is_premium", true)
                                        putBoolean("is_relative", isRelative)
                                        putString("user_email", targetEmail)
                                        putString("user_name", effectiveLoginName)
                                        putString("user_display_name", effectiveLoginName)
                                        putString("user_custom_id", finalCustomId)
                                        putString("user_tag", UserIdentifierManager.formatDisplay(finalCustomId))
                                        putString("user_account_number", accountNumber)
                                        putString("user_name_with_account", nameWithAccount)
                                        if (rememberMe) putString("saved_login_email", trimmedInput) else remove("saved_login_email")
                                        apply()
                                    }
                                    SupabaseRepository.getInstance().emitLocalAuthenticated(
                                        email = targetEmail,
                                        isAdmin = finalIsAdmin
                                    )
                                    CloudServices.syncAdminClaim(context)
                                    val firstBadge = if (UserIdentifierManager.isFirstAccount(accountNumber)) " (أول حساب مسجل 👑)" else ""
                                    Toast.makeText(
                                        context, 
                                        "مرحباً بك! تم التوثيق برقم الحساب: $accountNumber$firstBadge", 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onLoginSuccess(finalIsAdmin)
                                } else {
                                    val descriptiveError = diagResult.userFriendlyErrorMessage 
                                        ?: "فشلت المصادقة: لم يتم العثور على جلسة سحابية مفعلة (Session Null)."
                                    authErrorMessage = descriptiveError
                                    android.util.Log.e("LoginScreen", "[AuthDiagnostics] Login Failed -> $descriptiveError (raw: ${diagResult.rawErrorMessage})")
                                    Toast.makeText(context, descriptiveError, Toast.LENGTH_LONG).show()
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = DeepSlate
                        ),
                        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "بدء تشغيل الاستوديو // ENTER",
                                color = DeepSlate,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onNavigateToForgotPassword) {
                    Text("نسيان الرمز؟", color = accentColor, fontFamily = CairoFont, fontSize = 12.sp)
                }
                TextButton(onClick = onNavigateToRegister) {
                    Text("بناء حساب جديد // REGISTER", color = Color.White.copy(alpha = 0.7f), fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Guest Entry Option
            OutlinedButton(
                onClick = {
                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putBoolean("is_logged_in", true)
                        putBoolean("is_guest", true)
                        putBoolean("is_admin", false)
                        putBoolean("is_developer", false)
                        putString("user_email", "guest@qabas.studio")
                        putString("user_name", "ضيف قبس")
                        putString("user_display_name", "ضيف قبس")
                        putString("user_custom_id", "guest")
                        putString("user_tag", "@guest")
                        remove("user_account_number")
                        remove("user_name_with_account")
                        apply()
                    }
                    SupabaseRepository.getInstance().emitLocalAuthenticated(
                        email = "guest@qabas.studio",
                        name = "ضيف قبس",
                        isAdmin = false
                    )
                    Toast.makeText(
                        context,
                        "تم الدخول كضيف 👤. زر البلاغات البرمجية ومحادثة المطور محجوبان ومخصصان للحسابات الموثقة.",
                        Toast.LENGTH_LONG
                    ).show()
                    onLoginSuccess(false)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = CutCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.75f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "الدخول السريع كضيف // GUEST MODE",
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ENCRYPTION: AES-256 // SUPABASE_AUTH // IDENTITY_FEDERATION",
                color = primaryColor.copy(alpha = 0.35f),
                fontFamily = RobotoMonoFont,
                fontSize = 8.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "بتسجيل دخولك أنت توافق على القواعد والسياسات الموحدة لاستخدام قبس 📜",
                color = primaryColor.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontFamily = CairoFont,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clickable { onNavigateToRules() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Setup Guide HUD Dialog
        if (showGoogleSetupDialog) {
            GoogleSupabaseSetupDialog(
                onDismiss = { showGoogleSetupDialog = false },
                onTestSignIn = {
                    showGoogleSetupDialog = false
                    handleGoogleSignIn()
                }
            )
        }
    }
}
