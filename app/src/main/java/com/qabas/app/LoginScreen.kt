package com.qabas.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.TestTags
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * شاشة تسجيل الدخول والمصادقة الأمنية بتصميم Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 * تدعم تسجيل الدخول الموحد بالبريد الإلكتروني، رقم الحساب الرسمي، أو المعرف المخصص وكلمة المرور عبر سحابة Supabase.
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
    var rememberMe by remember { mutableStateOf(true) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    /** هل سبب الخطأ اتصال بالشبكة؟ (يحدّد أزرار بطاقة الخطأ التفاعلية) */
    var authErrorIsNetwork by remember { mutableStateOf(false) }
    var showUnregisteredDialog by remember { mutableStateOf(false) }
    var unregisteredTargetEmail by remember { mutableStateOf("") }
    var unregisteredTargetPassword by remember { mutableStateOf("") }
    var isQuickRegistering by remember { mutableStateOf(false) }

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    // Pre-fill last saved email or custom identifier ONLY if rememberMe was explicitly saved
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val savedAccount = prefs.getString("saved_login_email", "")?.trim().orEmpty()
        if (savedAccount.isNotBlank()) {
            accountInput = savedAccount
            rememberMe = true
        } else {
            accountInput = ""
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Brand Header: Bold & Elegant Typography for Application Name (قَبَسْ سْتُودْيُو)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                // Micro HUD protocol pill
                Surface(
                    color = primaryColor.copy(alpha = 0.08f),
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.35f)),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "SECURE AUTH // بروتوكول الوصول الذكي",
                        color = primaryColor,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                // App Name in bold, elegant Arabic typography with radiant metallic-gold & cyan gradient
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, primaryColor.copy(alpha = 0.8f))))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "قَبَسْ سْتُودْيُو",
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    primaryColor,
                                    Color.White,
                                    AiCyan
                                )
                            ),
                            fontSize = 32.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(Brush.horizontalGradient(listOf(primaryColor.copy(alpha = 0.8f), Color.Transparent)))
                    )
                }

                Text(
                    text = "QABAS STUDIO // فضاء المونتاج القرآني والذكاء الاصطناعي",
                    color = accentColor.copy(alpha = 0.85f),
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Subtitle: بوابة الوصول الرقمية (maintains test contract and screen semantic identity)
                Text(
                    text = "بوابة الوصول الرقمية",
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Minimalist Linear Tech Divider
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(24.dp, 1.5.dp).background(primaryColor.copy(alpha = 0.5f)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(4.dp).background(accentColor, CutCornerShape(1.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.size(24.dp, 1.5.dp).background(primaryColor.copy(alpha = 0.5f)))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cloud Link Status Ribbon (Compact Spatial HUD)
            Surface(
                color = CardSurface.copy(alpha = 0.5f),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (SupabaseServices.isSupabaseAvailable) primaryColor else Color(0xFFF59E0B),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (SupabaseServices.isSupabaseAvailable) "SUPABASE CLOUD // ONLINE" else "LOCAL CACHE // STANDBY",
                            color = Color.White.copy(alpha = 0.85f),
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    StrictBadge(
                        text = "SECURE PROTOCOL",
                        color = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Login Glass Card (Compact & Refined Fields)
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent)),
                        shape = CutCornerShape(14.dp)
                    ),
                shape = CutCornerShape(14.dp),
                backgroundColor = CardSurface.copy(alpha = 0.7f),
                borderColor = Color.White.copy(alpha = 0.05f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Fields Header with dynamic mode badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(modifier = Modifier.size(10.dp, 2.dp).background(primaryColor))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "بيانات الهوية والمصادقة",
                                color = Color.White.copy(alpha = 0.95f),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        val trimmedInput = accountInput.trim()
                        val (modeBadgeText, modeBadgeColor) = when {
                            trimmedInput.isEmpty() -> "AWAITING CREDENTIALS" to Color.Gray.copy(alpha = 0.7f)
                            trimmedInput.contains("@") -> "EMAIL PROTOCOL" to AiCyan
                            trimmedInput.all { it.isDigit() } -> "ACCOUNT #$trimmedInput" to primaryColor
                            else -> "CUSTOM USER ID" to accentColor
                        }

                        StrictBadge(
                            text = modeBadgeText,
                            color = modeBadgeColor
                        )
                    }

                    // محاولة الدخول الموحّدة: يستدعيها زر الإدخال وزر «إعادة المحاولة» في بطاقة الخطأ
                    val submitLogin: () -> Unit = submitLogin@{
                            val trimmedInput = accountInput.trim()
                            if (trimmedInput.isBlank() || password.isBlank()) {
                                authErrorMessage = "الرجاء إدخال البريد أو المعرّف الخاص وكلمة المرور"
                                authErrorIsNetwork = false
                                Toast.makeText(context, authErrorMessage, Toast.LENGTH_SHORT).show()
                                return@submitLogin
                            }
                            authErrorMessage = null
                            authErrorIsNetwork = false
                            isLoading = true
                            coroutineScope.launch {
                                // 1. فك تشفير المعرّف الخاص أو البريد
                                val resolvedPair = UserIdentifierManager.resolveIdentifierToEmail(trimmedInput, context)
                                if (resolvedPair == null) {
                                    val err = "تعذر العثور على حساب مرتبط بالمعرّف '$trimmedInput'. يرجى التأكد من صحة المعرّف أو تسجيل الدخول ببريدك الإلكتروني (Gmail)."
                                    authErrorMessage = err
                                    authErrorIsNetwork = false
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
                                    var finalCustomId = if (detectedCustomId.isNotBlank()) {
                                        UserIdentifierManager.saveCustomId(context, detectedCustomId, targetEmail)
                                        detectedCustomId
                                    } else {
                                        UserIdentifierManager.getOrCreateCustomId(context, targetEmail)
                                    }
                                    var accountNumber = UserIdentifierManager.ensureCloudAccountNumber(context, targetEmail)

                                    var canonicalUserId = externalId
                                    var effectiveLoginName = targetEmail.substringBefore('@')

                                    if (SupabaseServices.isSupabaseAvailable) {
                                        val existing = SupabaseServices.Database.ensureUser(canonicalUserId, lowerEmail, null, finalCustomId, accountNumber)
                                        if (existing != null) {
                                            if (existing.id.isNotBlank()) canonicalUserId = existing.id
                                            if (existing.type.isNotBlank()) userType = existing.type
                                            // الاسم المخزَّن هو المصدر: لا نعيد كتابته باسم البريد عند كل دخول
                                            if (!existing.name.isNullOrBlank()) effectiveLoginName = existing.name
                                            if (!existing.customId.isNullOrBlank()) {
                                                finalCustomId = existing.customId
                                                UserIdentifierManager.saveCustomId(context, finalCustomId, targetEmail)
                                            }
                                            if (!existing.accountNumber.isNullOrBlank()) {
                                                accountNumber = existing.accountNumber
                                                UserIdentifierManager.saveAccountNumber(context, targetEmail, accountNumber)
                                            }
                                        } else {
                                            SupabaseServices.Database.saveUser(
                                                id = canonicalUserId,
                                                name = effectiveLoginName,
                                                email = targetEmail,
                                                type = userType,
                                                customId = finalCustomId,
                                                accountNumber = accountNumber
                                            )
                                        }
                                    }

                                    // جلسة نشطة واحدة فقط: ارفض الدخول إذا كان الحساب حياً على جهاز آخر
                                    if (!ActiveSessionManager.claim(context, canonicalUserId)) {
                                        runCatching { SupabaseServices.Auth.logout() }
                                        authErrorMessage = "هذا الحساب نشط على جهاز آخر. سجّل الخروج من الجلسة الأخرى ثم حاول مجدداً."
                                        authErrorIsNetwork = false
                                        Toast.makeText(context, authErrorMessage, Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@launch
                                    }

                                    val finalIsAdmin = isAdmin || userType == "مطور" || userType == "خاص"
                                    val finalIsDeveloper = CloudServices.isOwnerAccount(lowerEmail) || userType == "مطور"

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
                                        putString("user_id", canonicalUserId)
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
                                    val cloudUser = if (SupabaseServices.isSupabaseAvailable) {
                                        SupabaseServices.Database.getUserByEmail(lowerEmail)
                                    } else null

                                    val rawErr = diagResult.rawErrorMessage ?: ""
                                    val isNotFound = (cloudUser == null) && (
                                        rawErr.contains("Invalid login credentials", ignoreCase = true) ||
                                        rawErr.contains("User not found", ignoreCase = true) ||
                                        rawErr.contains("invalid_grant", ignoreCase = true) ||
                                        rawErr.isBlank()
                                    )

                                    if (isNotFound && UserIdentifierManager.isEmail(targetEmail)) {
                                        unregisteredTargetEmail = targetEmail
                                        unregisteredTargetPassword = password
                                        showUnregisteredDialog = true
                                        authErrorMessage = null
                                    } else {
                                        val descriptiveError = diagResult.userFriendlyErrorMessage
                                            ?: "تعذّر تسجيل الدخول حالياً. أعد المحاولة أو تحقق من اتصالك بالإنترنت."
                                        authErrorMessage = descriptiveError
                                        authErrorIsNetwork = diagResult.isNetworkError
                                        android.util.Log.e("LoginScreen", "[AuthDiagnostics] Login Failed -> $descriptiveError (raw: ${diagResult.rawErrorMessage})")
                                        Toast.makeText(context, descriptiveError, Toast.LENGTH_LONG).show()
                                    }
                                }
                                isLoading = false
                            }
                    }

                    // بطاقة الخطأ التفاعلية: رسالة عربية آمنة (بلا روابط سحابة) + أزرار مباشرة
                    if (authErrorMessage != null) {
                        val errorTint = if (authErrorIsNetwork) Color(0xFFFFB020) else Color(0xFFFF4D4D)
                        Surface(
                            color = if (authErrorIsNetwork) Color(0xFF3A2A10) else Color(0xFF3E1218),
                            border = BorderStroke(1.dp, errorTint),
                            shape = CutCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (authErrorIsNetwork) Icons.Default.WifiOff else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = errorTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (authErrorIsNetwork) "تعذّر الاتصال" else "تعذّر تسجيل الدخول",
                                        color = errorTint,
                                        fontFamily = CairoFont,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { authErrorMessage = null },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إغلاق الخطأ",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = authErrorMessage ?: "",
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = submitLogin,
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        border = BorderStroke(1.dp, errorTint.copy(alpha = 0.7f)),
                                        shape = CutCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = errorTint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "إعادة المحاولة",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (authErrorIsNetwork) {
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Settings.ACTION_WIFI_SETTINGS)
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                }.onFailure {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Settings.ACTION_SETTINGS)
                                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            border = BorderStroke(1.dp, errorTint.copy(alpha = 0.7f)),
                                            shape = CutCornerShape(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = errorTint,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "فحص الاتصال",
                                                color = Color.White,
                                                fontFamily = CairoFont,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Unified Credential Field: Email, Custom ID, or Account Number
                    OutlinedTextField(
                        value = accountInput,
                        onValueChange = { 
                            accountInput = it
                            authErrorMessage = null
                        },
                        label = { Text("البريد الإلكتروني / رقم الحساب / المعرّف", fontFamily = CairoFont, fontSize = 11.sp) },
                        placeholder = { 
                            Text(
                                "user@gmail.com أو 000001 أو USR-8291", 
                                color = Color.Gray.copy(alpha = 0.45f), 
                                fontSize = 10.5.sp, 
                                fontFamily = CairoFont
                            ) 
                        },
                        textStyle = TextStyle(fontFamily = CairoFont, fontSize = 13.sp, color = Color.White),
                        leadingIcon = {
                            val trimmed = accountInput.trim()
                            val (iconVector, iconTint) = when {
                                trimmed.contains("@") -> Icons.Default.Email to AiCyan
                                trimmed.isNotEmpty() && trimmed.all { it.isDigit() } -> Icons.Default.AccountBox to primaryColor
                                trimmed.isNotEmpty() -> Icons.Default.Person to accentColor
                                else -> Icons.Default.AccountCircle to primaryColor.copy(alpha = 0.7f)
                            }
                            Icon(
                                imageVector = iconVector, 
                                contentDescription = null, 
                                tint = iconTint, 
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        trailingIcon = {
                            if (accountInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { accountInput = ""; authErrorMessage = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.LOGIN_SCREEN + "_account_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = primaryColor,
                            focusedContainerColor = Color.White.copy(alpha = 0.02f),
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = CutCornerShape(6.dp),
                        singleLine = true
                    )

                    // Cybernetic Dual-Login Hint Badge (Compact)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle, 
                            contentDescription = null, 
                            tint = primaryColor.copy(alpha = 0.7f), 
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "نظام موحد: يمكنك كتابة بريدك أو رقم الحساب أو المعرّف الخاص",
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            authErrorMessage = null
                        },
                        label = { Text("كلمة المرور", fontFamily = CairoFont, fontSize = 11.sp) },
                        textStyle = TextStyle(fontFamily = RobotoMonoFont, fontSize = 13.sp, color = Color.White),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = primaryColor.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = primaryColor,
                            focusedContainerColor = Color.White.copy(alpha = 0.02f),
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = CutCornerShape(6.dp),
                        singleLine = true
                    )

                    // Remember Me
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberMe = !rememberMe },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .border(1.dp, if (rememberMe) primaryColor else Color.Gray, CutCornerShape(3.dp))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (rememberMe) Box(modifier = Modifier.fillMaxSize().background(primaryColor, CutCornerShape(1.dp)))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تذكر بيانات الدخول برمجياً",
                            color = if (rememberMe) Color.White else Color.Gray,
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Submit Button (Compact Geometric Solid)
                    Button(
                        onClick = submitLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(primaryColor, AiCyan.copy(alpha = 0.85f))
                                ),
                                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(primaryColor.copy(alpha = 0.9f), AiCyan)
                                ),
                                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = DeepSlate
                        ),
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Login, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "بدء تشغيل الاستوديو // ENTER",
                                    color = DeepSlate,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // New User Registration Callout Banner (Compact & Anti-Squishing)
            Surface(
                color = primaryColor.copy(alpha = 0.08f),
                shape = CutCornerShape(8.dp),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        val currentTyped = accountInput.trim()
                        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                        if (UserIdentifierManager.isEmail(currentTyped)) {
                            prefs.edit().putString("draft_register_email", currentTyped).apply()
                        }
                        if (password.isNotBlank()) {
                            prefs.edit().putString("draft_register_password", password).apply()
                        }
                        onNavigateToRegister() 
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "مستخدم جديد؟ اضغط لإنشاء حساب",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "توليد المعرّف الخاص ورقم الحساب فوراً",
                                color = Color.White.copy(alpha = 0.6f),
                                fontFamily = CairoFont,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    StrictBadge(
                        text = "تسجيل ✦",
                        color = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Navigation Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onNavigateToForgotPassword,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("نسيان الرمز؟", color = accentColor, fontFamily = CairoFont, fontSize = 11.sp)
                }
                TextButton(
                    onClick = {
                        val currentTyped = accountInput.trim()
                        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                        if (UserIdentifierManager.isEmail(currentTyped)) {
                            prefs.edit().putString("draft_register_email", currentTyped).apply()
                        }
                        if (password.isNotBlank()) {
                            prefs.edit().putString("draft_register_password", password).apply()
                        }
                        onNavigateToRegister()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "إنشاء حساب جديد // REGISTER", 
                        color = primaryColor.copy(alpha = 0.9f), 
                        fontFamily = RobotoMonoFont, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 9.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Guest Entry Option (Compact)
            OutlinedButton(
                onClick = {
                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putBoolean("is_logged_in", false) // Guest mode is strictly unauthenticated
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
                    SupabaseRepository.getInstance().emitGuestMode("ضيف قبس")
                    Toast.makeText(
                        context,
                        "تم الدخول كضيف 👤. وضع الضيف محدود الصلاحيات ومخصص للتصفح فقط بدون جلسة موثقة.",
                        Toast.LENGTH_LONG
                    ).show()
                    onLoginSuccess(false)
                },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = CutCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.75f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الدخول السريع كضيف // GUEST MODE",
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ENCRYPTION: AES-256 // SUPABASE_AUTH // IDENTITY_FEDERATION",
                color = primaryColor.copy(alpha = 0.35f),
                fontFamily = RobotoMonoFont,
                fontSize = 8.sp,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "بتسجيل دخولك أنت توافق على القواعد والسياسات الموحدة لاستخدام قبس 📜",
                color = primaryColor.copy(alpha = 0.85f),
                fontSize = 10.5.sp,
                fontFamily = CairoFont,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clickable { onNavigateToRules() }
            )

            Spacer(modifier = Modifier.height(10.dp))
        }


        // Unregistered Account Prompt HUD Dialog
        if (showUnregisteredDialog) {
            UnregisteredAccountDialog(
                email = unregisteredTargetEmail,
                password = unregisteredTargetPassword,
                isCreating = isQuickRegistering,
                onDismiss = { showUnregisteredDialog = false },
                onNavigateToRegister = {
                    showUnregisteredDialog = false
                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("draft_register_email", unregisteredTargetEmail)
                        .putString("draft_register_password", unregisteredTargetPassword)
                        .apply()
                    onNavigateToRegister()
                },
                onQuickRegister = {
                    val passToUse = unregisteredTargetPassword.trim()
                    if (passToUse.isBlank()) {
                        Toast.makeText(context, "يرجى كتابة كلمة مرور جديدة لحسابك، أو الانتقال لشاشة التسجيل الكاملة ⚠️", Toast.LENGTH_LONG).show()
                    } else if (passToUse.length < 8) {
                        Toast.makeText(context, "يجب أن تتكون كلمة المرور من 8 خانات على الأقل لضمان أمان الحساب", Toast.LENGTH_LONG).show()
                    } else {
                        isQuickRegistering = true
                        coroutineScope.launch {
                            try {
                                var regSuccess = false
                                if (SupabaseServices.isSupabaseAvailable) {
                                    regSuccess = SupabaseServices.Auth.register(unregisteredTargetEmail, passToUse)
                                    if (!regSuccess) {
                                        regSuccess = SupabaseServices.Auth.login(unregisteredTargetEmail, passToUse)
                                    }
                                } else {
                                    regSuccess = true
                                }

                                if (regSuccess) {
                                    val lower = unregisteredTargetEmail.lowercase(java.util.Locale.ROOT)
                                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                                    val isAdmin = prefs.getBoolean("is_admin", false) || CloudServices.isOwnerAccount(lower)
                                    val userType = if (isAdmin) "مطور" else "مجاني"
                                    val userId = SupabaseServices.Auth.getCurrentUserId() ?: lower
                                    val defaultName = unregisteredTargetEmail.substringBefore('@')

                                    val customId = UserIdentifierManager.getOrCreateCustomId(context, unregisteredTargetEmail, defaultName)
                                    val accountNumber = UserIdentifierManager.ensureCloudAccountNumber(context, unregisteredTargetEmail, defaultName)

                                    var canonicalUserId = userId
                                    var effectiveName = defaultName

                                    if (SupabaseServices.isSupabaseAvailable) {
                                        val existing = SupabaseServices.Database.ensureUser(userId, lower, defaultName, customId, accountNumber)
                                        if (existing != null) {
                                            if (existing.id.isNotBlank()) canonicalUserId = existing.id
                                            if (!existing.name.isNullOrBlank()) effectiveName = existing.name
                                        }
                                        SupabaseServices.Database.saveUser(
                                            id = canonicalUserId,
                                            name = effectiveName,
                                            email = unregisteredTargetEmail,
                                            type = userType,
                                            customId = customId,
                                            accountNumber = accountNumber
                                        )
                                    }

                                    if (!ActiveSessionManager.claim(context, canonicalUserId)) {
                                        runCatching { SupabaseServices.Auth.logout() }
                                        Toast.makeText(context, "هذا الحساب نشط على جهاز آخر. سجّل الخروج من الجلسة الأخرى ثم حاول مجدداً.", Toast.LENGTH_LONG).show()
                                        isQuickRegistering = false
                                        return@launch
                                    }

                                    val nameWithAccount = UserIdentifierManager.formatDisplayNameWithAccount(effectiveName, accountNumber)

                                    LocalAccountRegistry.registerOrUpdateAccount(
                                        context = context,
                                        account = LocalAccountRegistry.AccountRecord(
                                            id = unregisteredTargetEmail,
                                            name = effectiveName,
                                            email = unregisteredTargetEmail,
                                            customId = customId,
                                            accountNumber = accountNumber,
                                            type = userType,
                                            status = "active"
                                        )
                                    )

                                    prefs.edit().apply {
                                        putBoolean("is_logged_in", true)
                                        putBoolean("is_guest", false)
                                        putBoolean("is_admin", isAdmin)
                                        putBoolean("is_developer", CloudServices.isOwnerAccount(lower))
                                        putBoolean("is_premium", true)
                                        putString("user_id", canonicalUserId)
                                        putString("user_email", unregisteredTargetEmail)
                                        putString("user_name", effectiveName)
                                        putString("user_display_name", effectiveName)
                                        putString("user_custom_id", customId)
                                        putString("user_tag", UserIdentifierManager.formatDisplay(customId))
                                        putString("user_account_number", accountNumber)
                                        putString("user_name_with_account", nameWithAccount)
                                        if (rememberMe) putString("saved_login_email", unregisteredTargetEmail) else remove("saved_login_email")
                                        apply()
                                    }

                                    SupabaseRepository.getInstance().emitLocalAuthenticated(
                                        email = unregisteredTargetEmail,
                                        isAdmin = isAdmin
                                    )
                                    CloudServices.syncAdminClaim(context)
                                    val firstBadge = if (UserIdentifierManager.isFirstAccount(accountNumber)) " (أول حساب مسجل 👑)" else ""
                                    Toast.makeText(
                                        context,
                                        "تم إنشاء الحساب والتوثيق بنجاح! رقم حسابك: $accountNumber$firstBadge",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    showUnregisteredDialog = false
                                    onLoginSuccess(isAdmin)
                                } else {
                                    Toast.makeText(context, "تعذر الإنشاء التلقائي. يرجى التوجه لشاشة التسجيل.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                // لا نعرض تفاصيل الاستثناء (قد تحمل رابط/مضيف السحابة) — نص آمن فقط
                                android.util.Log.e("LoginScreen", "quick register failed: ${e.message}", e)
                                Toast.makeText(
                                    context,
                                    SupabaseServices.Auth.describeAuthFailure(e).message,
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isQuickRegistering = false
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * نافذة حوار مخصصة عند إدخال بريد غير مسجل مسبقاً،
 * تتيح للمستخدم إما إنشاء الحساب فوراً والدخول برقم حساب رسمي،
 * أو الانتقال لشاشة إنشاء الحساب مع نقل البريد والرمز المدخل تلقائياً.
 */
@Composable
fun UnregisteredAccountDialog(
    email: String,
    password: String,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onQuickRegister: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()
    val nextAccNum = remember { UserIdentifierManager.peekNextAccountNumber(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = CutCornerShape(14.dp),
                color = DeepSlate,
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f)),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // HUD Badge & Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = primaryColor.copy(alpha = 0.12f),
                            shape = CutCornerShape(6.dp),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "IDENTITY NOT FOUND // 0x404",
                                color = primaryColor,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                letterSpacing = 1.sp
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Icon and Title
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(primaryColor.copy(alpha = 0.12f), CutCornerShape(12.dp))
                            .border(1.dp, primaryColor.copy(alpha = 0.35f), CutCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "البريد الإلكتروني غير مسجل",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "لم نجد حساباً مسجلاً في السحابة بهذا البريد، ولكن يمكنك تفعيله فوراً:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Email info pill
                    Surface(
                        color = Color.White.copy(alpha = 0.04f),
                        shape = CutCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("البريد المدخل:", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp)
                                Text(email, color = Color.White, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("رقم الحساب المتاح لك:", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp)
                                Surface(
                                    color = accentColor.copy(alpha = 0.15f),
                                    shape = CutCornerShape(4.dp),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "$nextAccNum ✦",
                                        color = accentColor,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action 1: Quick Create & Login
                    Button(
                        onClick = onQuickRegister,
                        enabled = !isCreating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = DeepSlate
                        ),
                        shape = CutCornerShape(8.dp)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إنشاء الحساب فوراً ومتابعة الدخول",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action 2: Go to full register screen
                    OutlinedButton(
                        onClick = onNavigateToRegister,
                        enabled = !isCreating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                        shape = CutCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AppRegistration, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "الانتقال لشاشة إنشاء الحساب (تخصيص البيانات)",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Action 3: Cancel / Edit
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isCreating
                    ) {
                        Text(
                            text = "تعديل البريد المدخل // CANCEL",
                            color = Color.White.copy(alpha = 0.6f),
                            fontFamily = RobotoMonoFont,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
