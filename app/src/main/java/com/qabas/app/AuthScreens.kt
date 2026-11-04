package com.qabas.app

import android.content.Context
import android.util.Log
import android.widget.Toast
import io.github.jan.supabase.auth.auth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.qabas.app.ui.components.GlassmorphicSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRules: () -> Unit = {}
) {
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

    // Public registration pre-checks only usernames; email existence stays private.
    var isCustomIdTaken by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingAvailability by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // A password must never be persisted in SharedPreferences.
        prefs.edit().remove("draft_register_email").remove("draft_register_password").apply()
    }

    // Debounced availability lookup checks public handles only, never email addresses.
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

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate)
    ) {
        // Background HUD
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
            // Back Button (Minimalist)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onNavigateToLogin) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = primaryColor)
                }
            }

            Text(
                text = "بناء هوية جديدة",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp
            )
            
            Text(
                text = "سجل انضمامك لمجتمع قبس الرقمي",
                color = TextSecondary.copy(alpha = 0.7f),
                fontFamily = CairoFont,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

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
                    // Live Identity Preview Card (Username + App Account Number side-by-side)
                    Surface(
                        color = Color(0xFF101726),
                        shape = CutCornerShape(10.dp),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp, 8.dp).background(primaryColor, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "الهوية الرقمية المخصصة للمستخدم الجديد",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                                Surface(
                                    color = primaryColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "توليد فوري تلقائي",
                                        color = primaryColor,
                                        fontFamily = CairoFont,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Username and Account Number Side-by-Side
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. Username (اليوزر)
                                Surface(
                                    color = Color.White.copy(alpha = 0.04f),
                                    shape = CutCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text(
                                            text = "اسم المستخدم (اليوزر)",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontFamily = CairoFont,
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = "@${customId.ifBlank { "qbs_user" }}",
                                            color = primaryColor,
                                            fontFamily = RobotoMonoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // 2. App Account Number (رقم الحساب بالتطبيق)
                                Surface(
                                    color = Color.White.copy(alpha = 0.04f),
                                    shape = CutCornerShape(6.dp),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text(
                                            text = "رقم الحساب بالتطبيق",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontFamily = CairoFont,
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = "يُنشأ بعد التسجيل",
                                            color = GoldPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
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
                                if (norm.isNotBlank()) {
                                    customId = norm
                                }
                            }
                        },
                        label = { Text("الاسم الكامل / اسم الحساب", fontFamily = CairoFont, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = CutCornerShape(8.dp),
                        singleLine = true
                    )

                    // Custom Identifier Field (Unique Handle / User ID)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = customId,
                            onValueChange = { customId = it.trim().removePrefix("@").replace(" ", "_") },
                            label = { Text("اسم المستخدم / المعرّف (اليوزر - Handle)", fontFamily = CairoFont, fontSize = 12.sp) },
                            placeholder = { Text("مثال: aliwalead أو qbs_ahmed", color = Color.Gray.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = CairoFont) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = primaryColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCheckingAvailability) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = primaryColor)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    IconButton(onClick = {
                                        customId = UserIdentifierManager.generateUniqueId(name.ifBlank { email })
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "توليد معرّف تلقائي", tint = primaryColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isCustomIdTaken == true) Color.Red else primaryColor,
                                unfocusedBorderColor = if (isCustomIdTaken == true) Color.Red.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = if (isCustomIdTaken == true) Color.Red else primaryColor,
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = CutCornerShape(8.dp),
                            singleLine = true
                        )
                        if (isCustomIdTaken == true) {
                            Text("⚠️ هذا المعرف مستخدم بالفعل، اختر واحداً آخر", color = Color.Red, fontSize = 9.sp, fontFamily = CairoFont, modifier = Modifier.padding(start = 8.dp))
                        } else if (isCustomIdTaken == false && customId.length >= 3) {
                            Text("✓ هذا المعرف متاح للاستخدام", color = Color(0xFF10B981), fontSize = 9.sp, fontFamily = CairoFont, modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("البريد الإلكتروني", fontFamily = CairoFont, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = primaryColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = primaryColor,
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = CutCornerShape(8.dp),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور", fontFamily = CairoFont, fontSize = 12.sp) },
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
                            unfocusedTextColor = Color.White
                        ),
                        shape = CutCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("تأكيد كلمة المرور", fontFamily = CairoFont, fontSize = 12.sp) },
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
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
                            unfocusedTextColor = Color.White
                        ),
                        shape = CutCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val trimmedEmail = email.trim()
                            if (name.isBlank() || trimmedEmail.isBlank() || password.isBlank()) {
                                Toast.makeText(context, "الرجاء تعبئة جميع الحقول", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password.trim() != confirmPassword.trim()) {
                                Toast.makeText(context, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password.length < 6) {
                                Toast.makeText(context, "كلمة المرور يجب أن تكون 6 أحرف على الأقل", Toast.LENGTH_SHORT).show()
                                return@Button
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
                                return@Button
                            }

                            isLoading = true
                            coroutineScope.launch {
                                // 🔍 فحص جازم أخير قبل محاولة التسجيل الفعلي
                                val finalCheck = if (SupabaseServices.isSupabaseAvailable && requestedCustomId != null) {
                                    SupabaseServices.Database.checkAccountAvailability(null, requestedCustomId).getOrNull()
                                } else null

                                if (finalCheck?.customIdTaken == true) {
                                    Toast.makeText(context, "المعرف الخاص (@$requestedCustomId) محجوز لمستخدم آخر، يرجى اختيار غيره.", Toast.LENGTH_LONG).show()
                                    isLoading = false
                                    return@launch
                                }

                                // The server performs the final unique check and atomically
                                // allocates the account number. A local pre-check is not authoritative.
                                val success = SupabaseServices.isSupabaseAvailable &&
                                    SupabaseServices.Auth.register(
                                        email = trimmedEmail,
                                        pass = password.trim(),
                                        customId = requestedCustomId,
                                        displayName = name.trim()
                                    )

                                if (success) {
                                    val authUid = SupabaseServices.Auth.awaitCurrentUserId(10_000L)
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
                                        runCatching { SupabaseServices.Auth.logout() }
                                        Toast.makeText(context, "لم يتم حفظ هوية الحساب في السحابة. لم يكتمل التسجيل.", Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@launch
                                    }

                                    val effectiveManualName = cloudUser.name?.takeIf { it.isNotBlank() }
                                        ?: name.trim().ifBlank { trimmedEmail.substringBefore('@') }
                                    val userType = cloudUser.type.ifBlank { "مجاني" }
                                    val isAdmin = userType == "مطور" || userType == "خاص"
                                    val isDeveloper = userType == "مطور" || CloudServices.isOwnerAccount(lowerEmail)

                                    UserIdentifierManager.saveCustomId(context, cloudCustomId, lowerEmail)
                                    UserIdentifierManager.saveAccountNumber(context, lowerEmail, accountNumber)

                                    // Active-session ownership is keyed by the immutable Auth UID.
                                    if (!ActiveSessionManager.claim(context, authUid)) {
                                        runCatching { SupabaseServices.Auth.logout() }
                                        Toast.makeText(context, "هذا الحساب نشط على جهاز آخر. سجّل الخروج من الجلسة الأخرى ثم حاول مجدداً.", Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@launch
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

                                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                                    prefs.edit().apply {
                                        putBoolean("is_logged_in", true)
                                        putBoolean("is_guest", false)
                                        putBoolean("is_admin", isAdmin)
                                        putBoolean("is_developer", isDeveloper)
                                        putString("user_id", authUid)
                                        putString("user_email", lowerEmail)
                                        putString("user_name", effectiveManualName)
                                        putString("user_display_name", effectiveManualName)
                                        putString("user_custom_id", cloudCustomId)
                                        putString("user_tag", UserIdentifierManager.formatDisplay(cloudCustomId))
                                        putString("user_account_number", accountNumber)
                                        putString("user_name_with_account", nameWithAccount)
                                        apply()
                                    }
                                    val firstBadge = if (UserIdentifierManager.isFirstAccount(accountNumber)) " (أول حساب مسجل 👑)" else ""
                                    Toast.makeText(
                                        context,
                                        "تم إنشاء الحساب بنجاح 🚀! اليوزر: @${cloudCustomId} | رقم الحساب: $accountNumber$firstBadge",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onRegisterSuccess()
                                } else {
                                    val state = SupabaseRepository.getInstance().authState.value
                                    val errorText = (state as? AuthState.Error)?.message.orEmpty()
                                    val message = when {
                                        errorText.contains("EMAIL_CONFIRMATION_REQUIRED", ignoreCase = true) ->
                                            "تم إنشاء طلب الحساب. تحقق من بريدك الإلكتروني وأكمل تأكيده، ثم سجّل الدخول."
                                        errorText.contains("users_custom_id_lower_unique", ignoreCase = true) ||
                                            errorText.contains("duplicate key", ignoreCase = true) ->
                                            "هذا اليوزر مستخدم بالفعل. اختر يوزراً آخر."
                                        !SupabaseServices.isSupabaseAvailable ->
                                            "الخدمات السحابية غير متاحة. لا يمكن إنشاء حساب دون اتصال بالسحابة."
                                        else -> "تعذر إنشاء الحساب أو حفظ هويته السحابية. حاول مجدداً."
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = DeepSlate),
                        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "تفعيل الحساب والبدء",
                                color = DeepSlate,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "بإنشاء حسابك أنت توافق على القواعد والسياسات الموحدة لاستخدام قبس 📜",
                color = primaryColor.copy(alpha = 0.85f),
                fontFamily = CairoFont,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clickable { onNavigateToRules() }
            )

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "لديك حساب بالفعل؟ ", color = Color.Gray, fontFamily = CairoFont, fontSize = 13.sp)
                TextButton(onClick = onNavigateToLogin) {
                    Text("سجل دخولك هنا", color = primaryColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isRecoveryVerified by PasswordRecoveryManager.isRecoveryVerified.collectAsState()
    var email by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isResetComplete by remember { mutableStateOf(false) }

    val primaryColor = qabasPrimary()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val step = 60.dp.toPx()
            for (i in 0..(w / step).toInt()) {
                drawLine(primaryColor.copy(alpha = 0.02f), Offset(i * step, 0f), Offset(i * step, h), 1f)
            }
            for (i in 0..(h / step).toInt()) {
                drawLine(primaryColor.copy(alpha = 0.02f), Offset(0f, i * step), Offset(w, i * step), 1f)
            }
        }

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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (isRecoveryVerified) {
                                runCatching { SupabaseConfig.client.auth.signOut() }
                                PasswordRecoveryManager.clear(context)
                            }
                            onNavigateToLogin()
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = primaryColor)
                }
            }

            if (isResetComplete) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "تم تغيير كلمة المرور بنجاح!",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "يمكنك الآن تسجيل الدخول باستخدام كلمة المرور الجديدة.",
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = DeepSlate),
                    shape = CutCornerShape(12.dp)
                ) {
                    Text("العودة لتسجيل الدخول", fontFamily = CairoFont, fontWeight = FontWeight.Black)
                }
            } else if (isRecoveryVerified) {
                Text(
                    text = "تعيين كلمة مرور جديدة",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "تم التحقق من رابط Supabase بنجاح. أدخل كلمة مرور جديدة ثم أكدها.",
                    color = TextSecondary.copy(alpha = 0.85f),
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth().border(
                        1.dp,
                        Color.White.copy(alpha = 0.05f),
                        CutCornerShape(16.dp)
                    ),
                    shape = CutCornerShape(16.dp),
                    backgroundColor = CardSurface.copy(alpha = 0.7f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("كلمة المرور الجديدة", fontFamily = CairoFont, fontSize = 12.sp) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            ),
                            shape = CutCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("تأكيد كلمة المرور الجديدة", fontFamily = CairoFont, fontSize = 12.sp) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            ),
                            shape = CutCornerShape(8.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val password = newPassword
                                if (password.length < 6) {
                                    Toast.makeText(
                                        context,
                                        "يجب أن تكون كلمة المرور 6 أحرف/أرقام على الأقل",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                                if (password != confirmPassword) {
                                    Toast.makeText(
                                        context,
                                        "كلمتا المرور غير متطابقتين",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }

                                isLoading = true
                                coroutineScope.launch {
                                    val updated = SupabaseServices.Auth.updateRecoveredPassword(password)
                                    isLoading = false
                                    if (updated) {
                                        runCatching { SupabaseConfig.client.auth.signOut() }
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
                            },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = DeepSlate
                            ),
                            shape = CutCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(24.dp))
                            } else {
                                Text("حفظ كلمة المرور الجديدة", fontFamily = CairoFont, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            } else if (isSubmitted) {
                Text(
                    text = "تم إرسال رابط الاستعادة",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "افتح أحدث رسالة من Supabase واضغط رابط إعادة تعيين كلمة المرور. سيعود الرابط إلى تطبيق قبس، وبعد التحقق ستظهر هنا خانات تعيين كلمة المرور.",
                    color = TextSecondary.copy(alpha = 0.9f),
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { isSubmitted = false },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = DeepSlate),
                    shape = CutCornerShape(12.dp)
                ) {
                    Text("إرسال رابط آخر", fontFamily = CairoFont, fontWeight = FontWeight.Black)
                }
            } else {
                Text(
                    text = "استعادة كلمة المرور",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp
                )
                Text(
                    text = "أدخل بريدك الإلكتروني لإرسال رابط آمن لإعادة التعيين",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني", fontFamily = CairoFont, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = CutCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (email.isBlank()) {
                            Toast.makeText(context, "الرجاء إدخال البريد الإلكتروني", Toast.LENGTH_SHORT).show()
                            return@Button
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
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = DeepSlate),
                    shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(24.dp))
                    } else {
                        Text("إرسال رابط الاستعادة", fontFamily = CairoFont, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
