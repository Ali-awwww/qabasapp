package com.qabas.app

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRules: () -> Unit = {}
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val draftEmail = remember { prefs.getString("draft_register_email", "") ?: "" }
    var name by remember { mutableStateOf("") }
    var customId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(draftEmail) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    // Batch 6 Priority 3 — Google one-tap sign-up (same shared flow as the
    // login screen) and the post-registration email-verification state.
    var isGoogleLoading by remember { mutableStateOf(false) }
    var showGoogleSetupDialog by remember { mutableStateOf(false) }
    var pendingVerificationEmail by remember { mutableStateOf<String?>(null) }

    val submitGoogleRegister: () -> Unit = {
        if (!isGoogleLoading) {
            if (!GoogleAuthHelper.isWebClientIdConfigured(context)) {
                Toast.makeText(context, GoogleSignInFlow.NOT_CONFIGURED_MESSAGE, Toast.LENGTH_LONG).show()
                showGoogleSetupDialog = true
            } else {
                isGoogleLoading = true
                val authRepository = SupabaseRepository.getInstance()
                authRepository.beginInteractiveAuthFlow()
                var authFlowAccepted = false
                val googleJob = coroutineScope.launch {
                    when (val outcome = GoogleSignInFlow.signIn(context)) {
                        is GoogleSignInFlow.Outcome.Success -> {
                            Toast.makeText(
                                context,
                                "تم إنشاء حسابك عبر Google بنجاح!" +
                                    if (outcome.guestMigrated) " — نُقلت بيانات الضيف إلى حسابك" else "",
                                Toast.LENGTH_LONG
                            ).show()
                            onRegisterSuccess()
                            authFlowAccepted = true
                        }
                        GoogleSignInFlow.Outcome.NotConfigured -> {
                            Toast.makeText(context, GoogleSignInFlow.NOT_CONFIGURED_MESSAGE, Toast.LENGTH_LONG).show()
                            showGoogleSetupDialog = true
                        }
                        GoogleSignInFlow.Outcome.Cancelled -> Unit
                        is GoogleSignInFlow.Outcome.Failed -> {
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
                submitGoogleRegister()
            }
        )
    }

    pendingVerificationEmail?.let { verificationEmail ->
        EmailVerificationPromptDialog(
            email = verificationEmail,
            onDismiss = { pendingVerificationEmail = null },
            onGoToLogin = {
                pendingVerificationEmail = null
                onNavigateToLogin()
            }
        )
    }

    var isCustomIdTaken by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingAvailability by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        prefs.edit().remove("draft_register_email").remove("draft_register_password").apply()
    }

    LaunchedEffect(customId) {
        val trimmedId = customId.trim()
        if (trimmedId.length < 3 || !SupabaseServices.isSupabaseAvailable) {
            isCustomIdTaken = null
            isCheckingAvailability = false
            return@LaunchedEffect
        }

        isCustomIdTaken = null
        delay(800)
        isCheckingAvailability = true
        try {
            SupabaseServices.Database.checkAccountAvailability(null, trimmedId)
                .onSuccess { response -> isCustomIdTaken = response.customIdTaken }
                .onFailure {
                    Log.w("RegisterScreen", "Handle availability check failed: ${it.message}")
                }
        } finally {
            isCheckingAvailability = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = theme.primary,
        unfocusedBorderColor = theme.separator,
        focusedLabelColor = theme.primary,
        unfocusedLabelColor = theme.textSecondary,
        focusedTextColor = theme.textPrimary,
        unfocusedTextColor = theme.textPrimary,
        cursorColor = theme.primary,
        focusedContainerColor = theme.secondarySurface.copy(alpha = 0.25f),
        unfocusedContainerColor = theme.secondarySurface.copy(alpha = 0.15f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = theme.primary)
                }
            }

            Text(
                text = "إنشاء حساب جديد",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            )

            Text(
                text = "سجل انضمامك لمجتمع خادم المسلم الرقمي",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Batch 6 Priority 3 — one-tap Google sign-up, above the email form.
            GoogleSignInButton(
                isLoading = isGoogleLoading,
                onClick = submitGoogleRegister,
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
                    text = "أو أنشئ حساباً بالبريد",
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = theme.separator.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppleCard(cornerRadius = 18.dp) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Live Identity Preview Card
                    Surface(
                        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.72f else 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "معاينة الهوية الرقمية",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Surface(
                                    color = theme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "توليد تلقائي",
                                        color = theme.primary,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = theme.cardSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text("اسم المستخدم", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 10.sp)
                                        Text(
                                            text = "@${customId.ifBlank { "qbs_user" }}",
                                            color = theme.primary,
                                            fontFamily = RobotoMonoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    color = theme.cardSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text("رقم الحساب", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 10.sp)
                                        Text(
                                            text = "يُنشأ تلقائياً",
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { newName ->
                            name = newName
                            if (customId.isBlank() || customId == "qbs_user") {
                                val norm = UserIdentifierManager.normalize(newName)
                                if (norm.isNotBlank()) customId = norm
                            }
                        },
                        label = { Text("الاسم الكامل / اسم الحساب", fontFamily = CairoFont, fontSize = 12.sp) },
                        textStyle = TextStyle(fontFamily = CairoFont, fontSize = 14.sp, color = theme.textPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = customId,
                            onValueChange = { customId = it.trim().removePrefix("@").replace(" ", "_") },
                            label = { Text("اسم المستخدم / المعرّف (Handle)", fontFamily = CairoFont, fontSize = 12.sp) },
                            placeholder = {
                                Text("مثال: aliwalead أو qbs_ahmed", color = theme.textSecondary.copy(alpha = 0.65f), fontSize = 12.sp, fontFamily = NotoSansFont)
                            },
                            textStyle = TextStyle(fontFamily = RobotoMonoFont, fontSize = 14.sp, color = theme.textPrimary),
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCheckingAvailability) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = theme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    IconButton(
                                        onClick = { customId = UserIdentifierManager.generateUniqueId(name.ifBlank { email }) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "توليد معرّف تلقائي", tint = theme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        if (isCustomIdTaken == true) {
                            Text("هذا المعرف مستخدم بالفعل، اختر واحداً آخر", color = theme.destructive, fontSize = 11.sp, fontFamily = CairoFont, modifier = Modifier.padding(start = 6.dp))
                        } else if (isCustomIdTaken == false && customId.length >= 3) {
                            Text("هذا المعرف متاح للاستخدام", color = theme.success, fontSize = 11.sp, fontFamily = CairoFont, modifier = Modifier.padding(start = 6.dp))
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("البريد الإلكتروني", fontFamily = CairoFont, fontSize = 12.sp) },
                        textStyle = TextStyle(fontFamily = NotoSansFont, fontSize = 14.sp, color = theme.textPrimary),
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور", fontFamily = CairoFont, fontSize = 12.sp) },
                        textStyle = TextStyle(fontFamily = NotoSansFont, fontSize = 14.sp, color = theme.textPrimary),
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
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("تأكيد كلمة المرور", fontFamily = CairoFont, fontSize = 12.sp) },
                        textStyle = TextStyle(fontFamily = NotoSansFont, fontSize = 14.sp, color = theme.textPrimary),
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isConfirmPasswordVisible) "إخفاء تأكيد كلمة المرور" else "إظهار تأكيد كلمة المرور",
                                    tint = theme.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    ApplePrimaryButton(
                        text = "إنشاء وتفعيل الحساب",
                        icon = Icons.Default.CheckCircle,
                        isLoading = isLoading,
                        onClick = {
                            if (isLoading) return@ApplePrimaryButton
                            val trimmedEmail = email.trim()
                            if (name.isBlank() || trimmedEmail.isBlank() || password.isBlank()) {
                                Toast.makeText(context, "الرجاء تعبئة جميع الحقول", Toast.LENGTH_SHORT).show()
                                return@ApplePrimaryButton
                            }
                            if (password.trim() != confirmPassword.trim()) {
                                Toast.makeText(context, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show()
                                return@ApplePrimaryButton
                            }
                            if (password.length < 8) {
                                Toast.makeText(context, "كلمة المرور يجب أن تكون 8 أحرف على الأقل", Toast.LENGTH_SHORT).show()
                                return@ApplePrimaryButton
                            }

                            val rawCustom = customId.trim().ifBlank {
                                val fromName = UserIdentifierManager.normalize(name)
                                if (fromName.isNotBlank()) fromName else trimmedEmail.substringBefore("@")
                            }
                            val requestedCustomId = UserIdentifierManager.normalize(rawCustom)
                                .takeIf { it.isNotBlank() }
                            if (requestedCustomId != null &&
                                (requestedCustomId.length !in 3..25 ||
                                    !requestedCustomId.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9_-]{2,24}$")))
                            ) {
                                Toast.makeText(context, "المعرّف يجب أن يكون بين 3 و25 حرفاً أو رقماً بالإنجليزية", Toast.LENGTH_LONG).show()
                                return@ApplePrimaryButton
                            }

                            isLoading = true
                            val authRepository = SupabaseRepository.getInstance()
                            authRepository.beginInteractiveAuthFlow()
                            var authFlowAccepted = false
                            val authJob = coroutineScope.launch {
                                val finalCheck = if (SupabaseServices.isSupabaseAvailable && requestedCustomId != null) {
                                    SupabaseServices.Database.checkAccountAvailability(null, requestedCustomId).getOrNull()
                                } else null

                                if (finalCheck?.customIdTaken == true) {
                                    Toast.makeText(context, "المعرف الخاص (@$requestedCustomId) محجوز لمستخدم آخر، يرجى اختيار غيره.", Toast.LENGTH_LONG).show()
                                    isLoading = false
                                    return@launch
                                }

                                val success = SupabaseServices.isSupabaseAvailable &&
                                    SupabaseServices.Auth.register(
                                        email = trimmedEmail,
                                        pass = password.trim(),
                                        customId = requestedCustomId,
                                        displayName = name.trim()
                                    )

                                if (success) {
                                    val authUid = SupabaseServices.Auth.getCurrentUserId()
                                    val lowerEmail = SupabaseServices.Auth.getCurrentUserEmail()
                                        ?.trim()?.lowercase(java.util.Locale.ROOT)
                                        ?: trimmedEmail.lowercase(java.util.Locale.ROOT)

                                    if (authUid.isNullOrBlank()) {
                                        Toast.makeText(context, "تعذر تأكيد جلسة الحساب. سجّل الدخول بعد إكمال التحقق من البريد.", Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@launch
                                    }

                                    val cloudUser = SupabaseServices.Database.ensureUser(
                                        externalId = authUid,
                                        email = lowerEmail,
                                        name = name.trim().ifBlank { trimmedEmail.substringBefore('@') },
                                        customId = requestedCustomId
                                    )

                                    val canonicalUserId = cloudUser?.id
                                    val cloudCustomId = cloudUser?.customId?.takeIf { it.isNotBlank() }
                                    val accountNumber = cloudUser?.accountNumber?.takeIf { it.isNotBlank() }
                                    if (canonicalUserId != authUid || cloudCustomId == null || accountNumber == null) {
                                        runCatching { SupabaseServices.Auth.logout() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                        Toast.makeText(context, "لم يتم حفظ هوية الحساب في السحابة. لم يكتمل التسجيل.", Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@launch
                                    }

                                    val effectiveManualName = cloudUser.name?.takeIf { it.isNotBlank() }
                                        ?: name.trim().ifBlank { trimmedEmail.substringBefore('@') }
                                    val cloudRole = SupabaseServices.Auth.getUserRoleFromMetadata()
                                    val isDeveloper = SupabaseServices.Auth.isDeveloperRole(cloudRole) ||
                                        lowerEmail.equals(CloudServices.OWNER_EMAIL, ignoreCase = true)
                                    val isAdmin = SupabaseServices.Auth.isAdminRole(cloudRole) ||
                                        CloudServices.isOwnerAccount(lowerEmail)
                                    val userType = when {
                                        isDeveloper -> "مطور"
                                        isAdmin -> "خاص"
                                        else -> "مجاني"
                                    }

                                    UserIdentifierManager.saveCustomId(context, cloudCustomId, lowerEmail)
                                    UserIdentifierManager.saveAccountNumber(context, lowerEmail, accountNumber)

                                    when (ActiveSessionManager.claim(context, authUid)) {
                                        ActiveSessionClaimResult.CLAIMED -> Unit
                                        ActiveSessionClaimResult.ACTIVE_ELSEWHERE -> {
                                            runCatching { ActiveSessionManager.endLocalSession(context) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                            Toast.makeText(context, "هذا الحساب نشط على جهاز آخر. سجّل الخروج منه ثم أعد المحاولة.", Toast.LENGTH_LONG).show()
                                            onNavigateToLogin()
                                            isLoading = false
                                            return@launch
                                        }
                                        ActiveSessionClaimResult.PROFILE_MISSING -> {
                                            runCatching { ActiveSessionManager.endLocalSession(context) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                            Toast.makeText(context, "تعذر العثور على ملف الحساب السحابي. لم يكتمل الدخول؛ تواصل مع الدعم.", Toast.LENGTH_LONG).show()
                                            onNavigateToLogin()
                                            isLoading = false
                                            return@launch
                                        }
                                        ActiveSessionClaimResult.UNAVAILABLE -> {
                                            runCatching { ActiveSessionManager.endLocalSession(context) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                            Toast.makeText(context, "تم إنشاء الحساب، لكن تعذر تأكيد الجلسة. انتقل لتسجيل الدخول وأعد المحاولة عند عودة الاتصال.", Toast.LENGTH_LONG).show()
                                            onNavigateToLogin()
                                            isLoading = false
                                            return@launch
                                        }
                                    }

                                    val nameWithAccount = UserIdentifierManager.formatDisplayNameWithAccount(effectiveManualName, accountNumber)
                                    LocalAccountRegistry.registerOrUpdateAccount(
                                        context = context,
                                        account = LocalAccountRegistry.AccountRecord(
                                            id = authUid,
                                            name = effectiveManualName,
                                            email = lowerEmail,
                                            customId = cloudCustomId,
                                            accountNumber = accountNumber,
                                            type = userType,
                                            status = "active",
                                            regDateMillis = System.currentTimeMillis()
                                        )
                                    )

                                    val prefsEdit = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                                    prefsEdit.edit().apply {
                                        putBoolean("is_logged_in", true)
                                        putBoolean("is_guest", false)
                                        putString("user_email", lowerEmail)
                                        putString("user_name", effectiveManualName)
                                        putString("user_display_name", effectiveManualName)
                                        putString("user_custom_id", cloudCustomId)
                                        putString("user_account_number", accountNumber)
                                        putString("user_name_with_account", nameWithAccount)
                                        apply()
                                    }
                                    // Batch 6 Priority 3: record the real email
                                    // confirmation state (gates cloud sync) and,
                                    // if this user arrived as a guest, migrate
                                    // their local data to the new account id.
                                    // If the address is still unconfirmed, stay
                                    // here and show the verification dialog
                                    // instead of entering as if verified.
                                    val emailConfirmedAfterRegister =
                                        SupabaseServices.Auth.refreshEmailConfirmationState()
                                    if (GuestUpgradeManager.shouldMigrate(context)) {
                                        runCatching {
                                            GuestUpgradeManager.migrateGuestDataToAccount(
                                                context = context,
                                                newUserId = authUid,
                                                newEmail = lowerEmail,
                                                emailConfirmed = emailConfirmedAfterRegister ?: true
                                            )
                                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                    }
                                    if (emailConfirmedAfterRegister == false) {
                                        pendingVerificationEmail = lowerEmail
                                        isLoading = false
                                        return@launch
                                    }
                                    val firstBadge = if (UserIdentifierManager.isFirstAccount(accountNumber)) " (أول حساب مسجل)" else ""
                                    Toast.makeText(
                                        context,
                                        "تم إنشاء الحساب بنجاح! اليوزر: @$cloudCustomId | رقم الحساب: $accountNumber$firstBadge",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onRegisterSuccess()
                                    authFlowAccepted = true
                                } else {
                                    val state = SupabaseRepository.getInstance().authState.value
                                    val errorText = (state as? AuthState.Error)?.message.orEmpty()
                                    val message = when {
                                        errorText.contains("EMAIL_CONFIRMATION_REQUIRED", ignoreCase = true) -> {
                                            // Registration was requested but no
                                            // session was issued until the
                                            // address is confirmed: show the
                                            // verification state, not just a toast.
                                            pendingVerificationEmail = trimmedEmail
                                            null
                                        }
                                        errorText.contains("users_custom_id_lower_unique", ignoreCase = true) ||
                                            errorText.contains("duplicate key", ignoreCase = true) ->
                                            "هذا اليوزر مستخدم بالفعل. اختر يوزراً آخر."
                                        !SupabaseServices.isSupabaseAvailable ->
                                            "الخدمات السحابية غير متاحة. لا يمكن إنشاء حساب دون اتصال بالسحابة."
                                        else -> "تعذر إنشاء الحساب أو حفظ هويته السحابية. حاول مجدداً."
                                    }
                                    if (message != null) {
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                                isLoading = false
                            }
                            authJob.invokeOnCompletion {
                                authRepository.completeInteractiveAuthFlow(accepted = authFlowAccepted)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onNavigateToRules) {
                Text(
                    text = "بإنشاء حسابك أنت توافق على القواعد والسياسات الموحدة لاستخدام خادم المسلم",
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "لديك حساب بالفعل؟ ", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 13.sp)
                TextButton(onClick = onNavigateToLogin) {
                    Text("سجل دخولك هنا", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onNavigateToLogin: () -> Unit
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isRecoveryVerified by PasswordRecoveryManager.isRecoveryVerified.collectAsState()
    var email by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isResetComplete by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = theme.primary,
        unfocusedBorderColor = theme.separator,
        focusedLabelColor = theme.primary,
        unfocusedLabelColor = theme.textSecondary,
        focusedTextColor = theme.textPrimary,
        unfocusedTextColor = theme.textPrimary,
        cursorColor = theme.primary,
        focusedContainerColor = theme.secondarySurface.copy(alpha = 0.25f),
        unfocusedContainerColor = theme.secondarySurface.copy(alpha = 0.15f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (isRecoveryVerified) {
                                runCatching { SupabaseConfig.client.auth.signOut() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                PasswordRecoveryManager.clear(context)
                            }
                            onNavigateToLogin()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = theme.primary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isResetComplete) {
                Surface(
                    shape = CircleShape,
                    color = theme.success.copy(alpha = 0.14f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.success, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "تم تغيير كلمة المرور بنجاح!",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "يمكنك الآن تسجيل الدخول باستخدام كلمة المرور الجديدة.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(28.dp))
                ApplePrimaryButton(
                    text = "العودة لتسجيل الدخول",
                    onClick = onNavigateToLogin
                )
            } else if (isRecoveryVerified) {
                Text(
                    text = "تعيين كلمة مرور جديدة",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "تم التحقق من رابط Supabase بنجاح. أدخل كلمة مرور جديدة ثم أكدها.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))

                AppleCard(cornerRadius = 18.dp) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("كلمة المرور الجديدة", fontFamily = CairoFont, fontSize = 12.sp) },
                            visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { isNewPasswordVisible = !isNewPasswordVisible },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isNewPasswordVisible) "إخفاء كلمة المرور الجديدة" else "إظهار كلمة المرور الجديدة",
                                        tint = theme.textSecondary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("تأكيد كلمة المرور الجديدة", fontFamily = CairoFont, fontSize = 12.sp) },
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isConfirmPasswordVisible) "إخفاء تأكيد كلمة المرور" else "إظهار تأكيد كلمة المرور",
                                        tint = theme.textSecondary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        ApplePrimaryButton(
                            text = "حفظ كلمة المرور الجديدة",
                            isLoading = isLoading,
                            onClick = {
                                val pass = newPassword
                                if (pass.length < 8) {
                                    Toast.makeText(context, "يجب أن تكون كلمة المرور 8 أحرف/أرقام على الأقل", Toast.LENGTH_SHORT).show()
                                    return@ApplePrimaryButton
                                }
                                if (pass != confirmPassword) {
                                    Toast.makeText(context, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show()
                                    return@ApplePrimaryButton
                                }

                                isLoading = true
                                coroutineScope.launch {
                                    val updated = SupabaseServices.Auth.updateRecoveredPassword(pass)
                                    isLoading = false
                                    if (updated) {
                                        runCatching { SupabaseConfig.client.auth.signOut() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                        PasswordRecoveryManager.clear(context)
                                        newPassword = ""
                                        confirmPassword = ""
                                        isResetComplete = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "تعذّر تحديث كلمة المرور. قد تكون الجلسة انتهت؛ أرسل رابط استعادة جديداً.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            } else if (isSubmitted) {
                Surface(
                    shape = CircleShape,
                    color = theme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = theme.primary, modifier = Modifier.size(38.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "تم إرسال رابط الاستعادة",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "افتح أحدث رسالة من Supabase واضغط رابط إعادة تعيين كلمة المرور. سيعود الرابط إلى تطبيق خادم المسلم، وبعد التحقق ستظهر هنا خانات تعيين كلمة المرور.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                ApplePrimaryButton(
                    text = "إرسال رابط آخر",
                    onClick = { isSubmitted = false }
                )
            } else {
                Text(
                    text = "استعادة كلمة المرور",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
                Text(
                    text = "أدخل بريدك الإلكتروني لإرسال رابط آمن لإعادة التعيين",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))

                AppleCard(cornerRadius = 18.dp) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("البريد الإلكتروني", fontFamily = CairoFont, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        ApplePrimaryButton(
                            text = "إرسال رابط الاستعادة",
                            isLoading = isLoading,
                            onClick = {
                                if (email.isBlank()) {
                                    Toast.makeText(context, "الرجاء إدخال البريد الإلكتروني", Toast.LENGTH_SHORT).show()
                                    return@ApplePrimaryButton
                                }
                                isLoading = true
                                coroutineScope.launch {
                                    val success = SupabaseServices.Auth.resetPassword(email.trim())
                                    isLoading = false
                                    if (success) {
                                        isSubmitted = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "فشل إرسال رابط الاستعادة. تحقق من الاتصال ثم حاول مجدداً.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
