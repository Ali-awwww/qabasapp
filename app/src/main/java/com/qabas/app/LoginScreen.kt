package com.qabas.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.TestTags
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * شاشة تسجيل الدخول والمصادقة بتصميم Apple HIG للوضعين الفاتح والداكن.
 * تدعم تسجيل الدخول بالبريد الإلكتروني أو رقم الحساب أو المعرف المخصص عبر Supabase.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (Boolean) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToRules: () -> Unit = {}
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var accountInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var authErrorIsNetwork by remember { mutableStateOf(false) }
    // Batch 6 Priority 3 — Google one-tap sign-in (was wired to nothing on
    // this screen). Failure when the client id is unset is graceful, never
    // a hard failure; the setup dialog stays available for the developer.
    var isGoogleLoading by remember { mutableStateOf(false) }
    var showGoogleSetupDialog by remember { mutableStateOf(false) }

    val submitGoogleLogin: () -> Unit = {
        if (!isGoogleLoading) {
            if (!GoogleAuthHelper.isWebClientIdConfigured(context)) {
                authErrorMessage = GoogleSignInFlow.NOT_CONFIGURED_MESSAGE
                authErrorIsNetwork = false
                showGoogleSetupDialog = true
            } else {
                authErrorMessage = null
                authErrorIsNetwork = false
                isGoogleLoading = true
                val authRepository = SupabaseRepository.getInstance()
                authRepository.beginInteractiveAuthFlow()
                var authFlowAccepted = false
                val googleJob = coroutineScope.launch {
                    when (val outcome = GoogleSignInFlow.signIn(context)) {
                        is GoogleSignInFlow.Outcome.Success -> {
                            Toast.makeText(
                                context,
                                "مرحباً بك! تم تسجيل الدخول عبر Google" +
                                    if (outcome.guestMigrated) " — نُقلت بيانات الضيف إلى حسابك" else "",
                                Toast.LENGTH_SHORT
                            ).show()
                            onLoginSuccess(outcome.isAdmin)
                            authFlowAccepted = true
                        }
                        GoogleSignInFlow.Outcome.NotConfigured -> {
                            authErrorMessage = GoogleSignInFlow.NOT_CONFIGURED_MESSAGE
                            authErrorIsNetwork = false
                            showGoogleSetupDialog = true
                        }
                        GoogleSignInFlow.Outcome.Cancelled -> Unit
                        is GoogleSignInFlow.Outcome.Failed -> {
                            authErrorMessage = outcome.message
                            authErrorIsNetwork = false
                            Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    isGoogleLoading = false
                }
                googleJob.invokeOnCompletion {
                    authRepository.completeInteractiveAuthFlow(accepted = authFlowAccepted)
                }
            }
        }
    }

    if (showGoogleSetupDialog) {
        GoogleSupabaseSetupDialog(
            onDismiss = { showGoogleSetupDialog = false },
            onTestSignIn = {
                showGoogleSetupDialog = false
                submitGoogleLogin()
            }
        )
    }

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
            .background(theme.background)
            .testTag(TestTags.LOGIN_SCREEN)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Brand Mark & Title
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = theme.primary.copy(alpha = if (theme.isDark) 0.18f else 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "شعار قبس",
                        tint = theme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "قبس ستوديو",
                color = theme.textPrimary,
                fontSize = 30.sp,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Subtitle maintains test contract and semantic identity
            Text(
                text = "بوابة الوصول الرقمية",
                color = theme.primary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Text(
                text = "استوديو الإنتاج الدعوي والمحتوى الإسلامي الذكي",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Cloud Link Status Pill
            Surface(
                color = theme.cardSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (SupabaseServices.isSupabaseAvailable) theme.success else theme.warning
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (SupabaseServices.isSupabaseAvailable) "متصل بالسحابة (Supabase)" else "وضع التخزين المحلي المؤقت",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Surface(
                        color = theme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "اتصال مشفّر",
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Batch 6 Priority 3 — one-tap Google sign-in, above the email form.
            GoogleSignInButton(
                isLoading = isGoogleLoading,
                onClick = submitGoogleLogin,
                onConfigureClick = { showGoogleSetupDialog = true },
                text = "متابعة مع Google"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = theme.separator.copy(alpha = 0.5f))
                Text(
                    text = "أو سجّل بالبريد",
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = theme.separator.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Credentials Card
            AppleCard(cornerRadius = 18.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "بيانات الدخول",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        val trimmedInput = accountInput.trim()
                        val (modeBadgeText, modeBadgeColor) = when {
                            trimmedInput.isEmpty() -> "في انتظار البيانات" to theme.textSecondary
                            trimmedInput.contains("@") -> "البريد الإلكتروني" to theme.accent
                            trimmedInput.all { it.isDigit() } -> "حساب #$trimmedInput" to theme.primary
                            else -> "معرّف مخصص" to theme.primary
                        }

                        Surface(
                            color = modeBadgeColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = modeBadgeText,
                                color = modeBadgeColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    val submitLogin: () -> Unit = submitLogin@{
                        if (isLoading) return@submitLogin
                        val trimmedInput = accountInput.trim()
                        if (trimmedInput.isBlank() || password.isBlank()) {
                            authErrorMessage = "الرجاء إدخال البريد أو اليوزر أو رقم الحساب وكلمة المرور"
                            authErrorIsNetwork = false
                            Toast.makeText(context, authErrorMessage, Toast.LENGTH_SHORT).show()
                            return@submitLogin
                        }

                        authErrorMessage = null
                        authErrorIsNetwork = false
                        isLoading = true
                        val authRepository = SupabaseRepository.getInstance()
                        authRepository.beginInteractiveAuthFlow()
                        var authFlowAccepted = false
                        val authJob = coroutineScope.launch {
                            val emailMode = UserIdentifierManager.isEmail(trimmedInput)
                            val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                            val isRelative = prefs.getBoolean("is_relative", false)
                            var diagnostic: SupabaseServices.AuthDiagnosticsResult? = null
                            var loginSucceeded = false

                            if (emailMode) {
                                diagnostic = SupabaseRepository.getInstance()
                                    .diagnoseSignInAttempt(trimmedInput, password)
                                loginSucceeded = diagnostic?.isSuccess == true &&
                                    diagnostic?.hasSession == true
                            } else {
                                loginSucceeded = SupabaseRepository.getInstance()
                                    .loginWithIdentifier(trimmedInput, password)
                                    .getOrDefault(false)
                            }

                            if (loginSucceeded) {
                                val account = authRepository.authState.value.authenticatedSessionSnapshot()
                                if (account == null) {
                                    authErrorMessage = "تعذر تجهيز حالة الحساب الموثقة. تحقق من الاتصال ثم أعد المحاولة."
                                    authErrorIsNetwork = true
                                    isLoading = false
                                    return@launch
                                }
                                val authUid = account.userId
                                val authEmail = account.email?.trim()?.lowercase(java.util.Locale.ROOT)
                                    ?.takeIf { it.isNotBlank() }
                                if (authEmail == null) {
                                    authErrorMessage = "تعذر قراءة بريد الحساب من حالة المصادقة. أعد المحاولة."
                                    authErrorIsNetwork = false
                                    isLoading = false
                                    return@launch
                                }

                                val cloudCustomId = account.customId?.takeIf { it.isNotBlank() }
                                val accountNumber = account.accountNumber?.takeIf { it.isNotBlank() }
                                if (cloudCustomId == null || accountNumber == null) {
                                    authErrorMessage = "بيانات الحساب الموثقة غير مكتملة. لم يتم إنهاء تسجيل الدخول."
                                    authErrorIsNetwork = false
                                    isLoading = false
                                    return@launch
                                }

                                val effectiveLoginName = account.displayName?.takeIf { it.isNotBlank() }
                                    ?: authEmail.substringBefore('@')
                                val finalIsDeveloper = SupabaseServices.Auth.isDeveloperRole(account.role) ||
                                    CloudServices.isOwnerAccount(authEmail)
                                val finalIsAdmin = account.isAdmin
                                val userType = when {
                                    finalIsDeveloper -> "مطور"
                                    finalIsAdmin -> "خاص"
                                    else -> "مجاني"
                                }
                                val nameWithAccount = UserIdentifierManager.formatDisplayNameWithAccount(effectiveLoginName, accountNumber)

                                UserIdentifierManager.saveCustomId(context, cloudCustomId, authEmail)
                                UserIdentifierManager.saveAccountNumber(context, authEmail, accountNumber)

                                when (ActiveSessionManager.claim(context, authUid)) {
                                    ActiveSessionClaimResult.CLAIMED -> Unit
                                    ActiveSessionClaimResult.ACTIVE_ELSEWHERE -> {
                                        runCatching { ActiveSessionManager.endLocalSession(context) }
                                        authErrorMessage = "هذا الحساب نشط على جهاز آخر. سجّل الخروج منه ثم أعد المحاولة."
                                        authErrorIsNetwork = false
                                        Toast.makeText(context, authErrorMessage, Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@launch
                                    }
                                    ActiveSessionClaimResult.PROFILE_MISSING -> {
                                        runCatching { ActiveSessionManager.endLocalSession(context) }
                                        authErrorMessage = "تعذر العثور على ملف حسابك في السحابة. أعد المحاولة لاحقًا أو تواصل مع الدعم."
                                        authErrorIsNetwork = false
                                        Toast.makeText(context, authErrorMessage, Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@launch
                                    }
                                    ActiveSessionClaimResult.UNAVAILABLE -> {
                                        runCatching { ActiveSessionManager.endLocalSession(context) }
                                        authErrorMessage = "تعذر التحقق من جلستك الآن. تحقق من الإنترنت ثم أعد المحاولة."
                                        authErrorIsNetwork = true
                                        Toast.makeText(context, authErrorMessage, Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@launch
                                    }
                                }

                                LocalAccountRegistry.registerOrUpdateAccount(
                                    context = context,
                                    account = LocalAccountRegistry.AccountRecord(
                                        id = authUid,
                                        name = effectiveLoginName,
                                        email = authEmail,
                                        customId = cloudCustomId,
                                        accountNumber = accountNumber,
                                        type = userType,
                                        status = "active"
                                    )
                                )

                                prefs.edit().apply {
                                    putBoolean("is_logged_in", true)
                                    putBoolean("is_guest", false)
                                    putBoolean("is_admin", finalIsAdmin)
                                    putBoolean("is_developer", finalIsDeveloper)
                                    putBoolean("is_relative", isRelative)
                                    putString("user_id", authUid)
                                    putString("user_email", authEmail)
                                    putString("user_name", effectiveLoginName)
                                    putString("user_display_name", effectiveLoginName)
                                    putString("user_custom_id", cloudCustomId)
                                    putString("user_tag", UserIdentifierManager.formatDisplay(cloudCustomId))
                                    putString("user_account_number", accountNumber)
                                    putString("user_name_with_account", nameWithAccount)
                                    if (rememberMe) putString("saved_login_email", authEmail) else remove("saved_login_email")
                                    apply()
                                }

                                CloudServices.syncAdminClaim(context)
                                // Batch 6 Priority 3: record the real email
                                // confirmation state (gates cloud sync) and, if
                                // this user arrived as a guest, migrate their
                                // local data to the new account id.
                                val emailConfirmedAfterLogin =
                                    SupabaseServices.Auth.refreshEmailConfirmationState() ?: true
                                if (GuestUpgradeManager.shouldMigrate(context)) {
                                    runCatching {
                                        GuestUpgradeManager.migrateGuestDataToAccount(
                                            context = context,
                                            newUserId = authUid,
                                            newEmail = authEmail,
                                            emailConfirmed = emailConfirmedAfterLogin
                                        )
                                    }
                                }
                                val firstBadge = if (UserIdentifierManager.isFirstAccount(accountNumber)) " (أول حساب مسجل)" else ""
                                Toast.makeText(
                                    context,
                                    "مرحباً بك! اليوزر: @$cloudCustomId | رقم الحساب: $accountNumber$firstBadge",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onLoginSuccess(finalIsAdmin)
                                authFlowAccepted = true
                            } else {
                                val rawError = diagnostic?.rawErrorMessage.orEmpty()
                                val invalidCredentials = rawError.contains("Invalid login credentials", ignoreCase = true) ||
                                    rawError.contains("invalid_grant", ignoreCase = true) ||
                                    rawError.contains("Invalid_credentials", ignoreCase = true)

                                if (emailMode && invalidCredentials) {
                                    authErrorMessage = "تعذّر تسجيل الدخول: البريد الإلكتروني أو كلمة المرور غير صحيحين. إذا كان هذا البريد مسجّلاً بالفعل، فلا تنشئ حساباً جديداً؛ تحقّق من كلمة المرور أو استخدم «نسيان الرمز؟» لاستعادتها."
                                    authErrorIsNetwork = false
                                    Toast.makeText(context, authErrorMessage, Toast.LENGTH_LONG).show()
                                } else {
                                    authErrorMessage = diagnostic?.userFriendlyErrorMessage
                                        ?: "بيانات الدخول غير صحيحة أو تعذر الوصول إلى خدمة المصادقة."
                                    authErrorIsNetwork = diagnostic?.isNetworkError == true
                                    Toast.makeText(context, authErrorMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                            isLoading = false
                        }
                        authJob.invokeOnCompletion {
                            authRepository.completeInteractiveAuthFlow(accepted = authFlowAccepted)
                        }
                    }

                    // Interactive Error Card
                    if (authErrorMessage != null) {
                        val errorTint = if (authErrorIsNetwork) theme.warning else theme.destructive
                        Surface(
                            color = errorTint.copy(alpha = if (theme.isDark) 0.16f else 0.10f),
                            border = BorderStroke(0.5.dp, errorTint.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
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
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { authErrorMessage = null },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إغلاق الخطأ",
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = authErrorMessage.orEmpty(),
                                    color = theme.textPrimary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = submitLogin,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp),
                                        border = BorderStroke(1.dp, errorTint.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = errorTint, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إعادة المحاولة", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (authErrorIsNetwork) {
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                }.onFailure {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp),
                                            border = BorderStroke(1.dp, errorTint.copy(alpha = 0.6f)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = null, tint = errorTint, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("فحص الاتصال", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Credential Input Field
                    OutlinedTextField(
                        value = accountInput,
                        onValueChange = {
                            accountInput = it
                            authErrorMessage = null
                        },
                        label = { Text("البريد الإلكتروني / رقم الحساب / المعرّف", fontFamily = CairoFont, fontSize = 12.sp) },
                        placeholder = {
                            Text(
                                "user@gmail.com أو 000001 أو USR-8291",
                                color = theme.textSecondary.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontFamily = NotoSansFont
                            )
                        },
                        textStyle = TextStyle(fontFamily = NotoSansFont, fontSize = 14.sp, color = theme.textPrimary),
                        leadingIcon = {
                            val trimmed = accountInput.trim()
                            val (iconVector, iconTint) = when {
                                trimmed.contains("@") -> Icons.Default.Email to theme.accent
                                trimmed.isNotEmpty() && trimmed.all { it.isDigit() } -> Icons.Default.AccountBox to theme.primary
                                trimmed.isNotEmpty() -> Icons.Default.Person to theme.primary
                                else -> Icons.Default.AccountCircle to theme.textSecondary
                            }
                            Icon(imageVector = iconVector, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
                        },
                        trailingIcon = {
                            if (accountInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { accountInput = ""; authErrorMessage = null },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "مسح الحقل", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.LOGIN_SCREEN + "_account_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.separator,
                            focusedLabelColor = theme.primary,
                            unfocusedLabelColor = theme.textSecondary,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            cursorColor = theme.primary,
                            focusedContainerColor = theme.secondarySurface.copy(alpha = 0.25f),
                            unfocusedContainerColor = theme.secondarySurface.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.success, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "نظام موحد: يمكنك كتابة بريدك أو رقم الحساب أو المعرّف الخاص",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
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
                        textStyle = TextStyle(fontFamily = NotoSansFont, fontSize = 14.sp, color = theme.textPrimary),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                    tint = theme.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.separator,
                            focusedLabelColor = theme.primary,
                            unfocusedLabelColor = theme.textSecondary,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            cursorColor = theme.primary,
                            focusedContainerColor = theme.secondarySurface.copy(alpha = 0.25f),
                            unfocusedContainerColor = theme.secondarySurface.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = rememberMe, role = Role.Checkbox, onValueChange = { rememberMe = it }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = theme.primary,
                                uncheckedColor = theme.textSecondary,
                                checkmarkColor = theme.onPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تذكر بيانات الدخول على هذا الجهاز",
                            color = if (rememberMe) theme.textPrimary else theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    ApplePrimaryButton(
                        text = "تسجيل الدخول",
                        icon = Icons.Default.Login,
                        isLoading = isLoading,
                        onClick = submitLogin
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Register Card Banner
            AppleCard(
                onClick = {
                    val currentTyped = accountInput.trim()
                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    if (UserIdentifierManager.isEmail(currentTyped)) {
                        prefs.edit().putString("draft_register_email", currentTyped).apply()
                    }
                    onNavigateToRegister()
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "مستخدم جديد؟ أنشئ حسابك الآن",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "توليد المعرّف الخاص ورقم الحساب فورياً",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = theme.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onNavigateToForgotPassword,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("نسيت كلمة المرور؟", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                TextButton(
                    onClick = {
                        val currentTyped = accountInput.trim()
                        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                        if (UserIdentifierManager.isEmail(currentTyped)) {
                            prefs.edit().putString("draft_register_email", currentTyped).apply()
                        }
                        onNavigateToRegister()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إنشاء حساب جديد", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            AppleSecondaryButton(
                text = "الدخول السريع كضيف للمعاينة",
                icon = Icons.Default.Person,
                onClick = {
                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putBoolean("is_logged_in", false)
                        putBoolean("is_guest", true)
                        putBoolean("is_admin", false)
                        putBoolean("is_developer", false)
                        remove("user_id")
                        remove("saved_login_email")
                        remove("user_account_number")
                        remove("user_name_with_account")
                        putString("user_email", "guest@qabas.studio")
                        putString("user_name", "ضيف قبس")
                        putString("user_display_name", "زائر مؤقت")
                        putString("user_custom_id", "guest")
                        putString("user_tag", "@guest")
                        remove("auth_token")
                        remove("refresh_token")
                        remove("user_api_key")
                        apply()
                    }
                    SupabaseRepository.getInstance().emitGuestMode("ضيف قبس")
                    Toast.makeText(
                        context,
                        "تم الدخول كضيف. هذا الوضع للمعاينة فقط؛ لا يتم حفظ البيانات أو مزامنتها مع السحابة بدون تسجيل دخول رسمي.",
                        Toast.LENGTH_LONG
                    ).show()
                    onLoginSuccess(false)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(onClick = onNavigateToRules) {
                Text(
                    text = "بتسجيل دخولك أنت توافق على القواعد والسياسات الموحدة لاستخدام قبس",
                    color = theme.primary,
                    fontSize = 11.5.sp,
                    fontFamily = CairoFont,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
