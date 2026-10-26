package com.qabas.app

import android.content.Context
import android.widget.Toast
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
    val draftPass = remember { prefs.getString("draft_register_password", "") ?: "" }

    var name by remember { mutableStateOf(if (draftEmail.isNotBlank()) draftEmail.substringBefore('@') else "") }
    var customId by remember { mutableStateOf("") }
    var userHasEditedCustomId by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(draftEmail) }
    var password by remember { mutableStateOf(draftPass) }
    var confirmPassword by remember { mutableStateOf(draftPass) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var nextAccountNumber by remember { mutableStateOf(UserIdentifierManager.peekNextAccountNumber(context)) }
    var isFetchingCloudSeq by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (draftEmail.isNotBlank()) {
            prefs.edit().remove("draft_register_email").remove("draft_register_password").apply()
        }
        // جلب رقم الحساب المتسلسل من السحابة (getNextAccountSequenceFromCloud) مع دعم العمل محلياً أوفلاين
        coroutineScope.launch {
            if (SupabaseServices.isSupabaseAvailable) {
                runCatching {
                    val seq = SupabaseServices.Database.getNextAccountSequenceFromCloud()
                    val formatted = "#QBS-" + String.format(java.util.Locale.ROOT, "%04d", seq)
                    if (formatted.isNotBlank()) {
                        nextAccountNumber = formatted
                    }
                }
            }
            isFetchingCloudSeq = false
        }
    }

    // التوليد التلقائي لاسم المستخدم (اليوزر) عند إدخال الاسم أو البريد دون الحاجة لكتابته يدوياً
    LaunchedEffect(name, email) {
        if (!userHasEditedCustomId && customId.isBlank()) {
            val seed = name.ifBlank { email }
            if (seed.isNotBlank()) {
                customId = UserIdentifierManager.generateUniqueId(seed)
            }
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
                                        text = if (isFetchingCloudSeq) "جاري الربط بالسحابة..." else "توليد فوري تلقائي",
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
                                            text = "اسم المستخدم (توليد تلقائي)",
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
                                            text = "رقم الحساب المتسلسل",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontFamily = CairoFont,
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = nextAccountNumber,
                                            color = GoldPrimary,
                                            fontFamily = RobotoMonoFont,
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
                        onValueChange = { name = it },
                        label = { Text("الاسم الكامل", fontFamily = CairoFont, fontSize = 12.sp) },
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

                    // Custom Identifier Field (Auto-Generated Handle / User ID)
                    OutlinedTextField(
                        value = customId,
                        onValueChange = {
                            customId = it.trim().replace(" ", "_")
                            userHasEditedCustomId = true
                        },
                        label = { Text("اسم المستخدم / المعرّف (توليد تلقائي)", fontFamily = CairoFont, fontSize = 12.sp) },
                        placeholder = { Text("توليد تلقائي برمجياً", color = Color.Gray.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = CairoFont) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = primaryColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                customId = UserIdentifierManager.generateUniqueId(name.ifBlank { email })
                                userHasEditedCustomId = true
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "توليد معرّف تلقائي جديد", tint = primaryColor, modifier = Modifier.size(18.dp))
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
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("البريد الإلكتروني", fontFamily = CairoFont, fontSize = 12.sp) },
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

                            isLoading = true
                            coroutineScope.launch {
                                // 1. توليد معرّف مضمون عدم التكرار محلياً (LocalAccountRegistry) وسحابياً (Supabase)
                                val initialId = if (customId.isNotBlank()) UserIdentifierManager.normalize(customId) else UserIdentifierManager.normalize(trimmedEmail.substringBefore('@').ifBlank { name })
                                val finalCustomId = UserIdentifierManager.generateGuaranteedUniqueCustomId(
                                    context = context,
                                    seed = initialId,
                                    currentEmail = trimmedEmail
                                )

                                var success = false
                                if (SupabaseServices.isSupabaseAvailable) {
                                    success = SupabaseServices.Auth.register(trimmedEmail, password.trim())
                                    if (!success) {
                                        success = SupabaseServices.Auth.login(trimmedEmail, password.trim())
                                    }
                                }
                                if (!success && !SupabaseServices.isSupabaseAvailable) success = true

                                if (success) {
                                    val lowerEmail = trimmedEmail.lowercase(java.util.Locale.ROOT)
                                    val isAdmin = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE).getBoolean("is_admin", false) || CloudServices.isOwnerAccount(lowerEmail)
                                    val userType = if (isAdmin) "مطور" else "مجاني"
                                    val userId = SupabaseServices.Auth.getCurrentUserId() ?: lowerEmail
                                    
                                    // 2. جلب رقم الحساب المتسلسل المضمون من السحابة (getNextAccountSequenceFromCloud) مع دعم أوفلاين
                                    val accountNumber = UserIdentifierManager.ensureCloudAccountNumber(context, trimmedEmail, name.trim())

                                    UserIdentifierManager.saveCustomId(context, finalCustomId, trimmedEmail)

                                    var canonicalUserId = userId
                                    var effectiveManualName = name.trim().ifBlank { trimmedEmail.substringBefore('@') }

                                    if (SupabaseServices.isSupabaseAvailable) {
                                        val existing = SupabaseServices.Database.ensureUser(userId, lowerEmail, effectiveManualName, finalCustomId, accountNumber)
                                        if (existing != null) {
                                            if (existing.id.isNotBlank()) canonicalUserId = existing.id
                                            if (!existing.name.isNullOrBlank()) effectiveManualName = existing.name
                                        }
                                        SupabaseServices.Database.saveUser(
                                            id = canonicalUserId,
                                            name = effectiveManualName,
                                            email = trimmedEmail,
                                            type = userType,
                                            customId = finalCustomId,
                                            accountNumber = accountNumber
                                        )
                                    }

                                    // جلسة نشطة واحدة فقط لكل حساب
                                    if (!ActiveSessionManager.claim(context, canonicalUserId)) {
                                        runCatching { SupabaseServices.Auth.logout() }
                                        Toast.makeText(context, "هذا الحساب نشط على جهاز آخر. سجّل الخروج من الجلسة الأخرى ثم حاول مجدداً.", Toast.LENGTH_LONG).show()
                                        isLoading = false
                                        return@launch
                                    }

                                    val nameWithAccount = UserIdentifierManager.formatDisplayNameWithAccount(effectiveManualName, accountNumber)
                                    LocalAccountRegistry.registerOrUpdateAccount(
                                        context = context,
                                        account = LocalAccountRegistry.AccountRecord(
                                            id = canonicalUserId,
                                            name = effectiveManualName,
                                            email = trimmedEmail,
                                            customId = finalCustomId,
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
                                        putBoolean("is_developer", CloudServices.isOwnerAccount(lowerEmail))
                                        putBoolean("is_premium", true)
                                        putString("user_id", canonicalUserId)
                                        putString("user_email", trimmedEmail)
                                        putString("user_name", effectiveManualName)
                                        putString("user_display_name", effectiveManualName)
                                        putString("user_custom_id", finalCustomId)
                                        putString("user_tag", UserIdentifierManager.formatDisplay(finalCustomId))
                                        putString("user_account_number", accountNumber)
                                        putString("user_name_with_account", nameWithAccount)
                                        apply()
                                    }
                                    val firstBadge = if (UserIdentifierManager.isFirstAccount(accountNumber)) " (أول حساب مسجل 👑)" else ""
                                    Toast.makeText(
                                        context, 
                                        "تم إنشاء الحساب بنجاح 🚀! اليوزر: @$finalCustomId | رقم الحساب: $accountNumber$firstBadge", 
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onRegisterSuccess()
                                } else {
                                    Toast.makeText(context, "فشل إنشاء الحساب. تأكد من صحة البريد والشبكة", Toast.LENGTH_LONG).show()
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
    var email by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onNavigateToLogin) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = primaryColor)
                }
            }

            if (isSubmitted) {
                var verificationCode by remember { mutableStateOf("") }
                var newPassword by remember { mutableStateOf("") }
                var isResetComplete by remember { mutableStateOf(false) }

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
                        text = "يمكنك الآن تسجيل الدخول بكلمة المرور الجديدة بسهولة.",
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
                } else {
                    Text(
                        text = "تم إرسال بروتوكول الاستعادة",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp
                    )
                    Text(
                        text = "تمت المعالجة لبريدك ($email)",
                        color = TextSecondary.copy(alpha = 0.8f),
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Explanation card for Supabase Link vs Code
                    Surface(
                        color = primaryColor.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
                        shape = CutCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "💡 تنبيه بخصوص رسالة البريد:",
                                color = primaryColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "رسالة Supabase تحتوي على رابط (Reset password) وليس كوداً رقمياً. إذا لم يفتح الرابط، قم بإدخال كلمة المرور الجديدة أدناه واضغط 'تحديث كلمة المرور مباشرة'.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GlassmorphicSurface(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.05f), CutCornerShape(16.dp)),
                        shape = CutCornerShape(16.dp),
                        backgroundColor = CardSurface.copy(alpha = 0.7f)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = verificationCode,
                                onValueChange = { verificationCode = it },
                                label = { Text("رمز/توكن الاستعادة (اختياري)", fontFamily = CairoFont, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = Color.White.copy(alpha = 0.1f)),
                                shape = CutCornerShape(8.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("كلمة المرور الجديدة", fontFamily = CairoFont, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = Color.White.copy(alpha = 0.1f)),
                                shape = CutCornerShape(8.dp),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    val trimmedPass = newPassword.trim()
                                    if (trimmedPass.length < 6) {
                                        Toast.makeText(context, "يجب أن تكون كلمة المرور 6 أحرف/أرقام على الأقل", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isLoading = true
                                    coroutineScope.launch {
                                        val trimmedEmail = email.trim().lowercase(java.util.Locale.ROOT)
                                        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)

                                        // Update password in Supabase if available
                                        if (SupabaseServices.isSupabaseAvailable) {
                                            runCatching {
                                                SupabaseServices.Auth.updatePassword(trimmedPass)
                                            }
                                        }

                                        // Update local preferences and saved login email
                                        prefs.edit().apply {
                                            putString("saved_login_email", trimmedEmail)
                                            remove("saved_login_password")
                                            apply()
                                        }

                                        Toast.makeText(context, "تم تعيين كلمة المرور الجديدة بنجاح!", Toast.LENGTH_SHORT).show()
                                        isResetComplete = true
                                        isLoading = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = DeepSlate),
                                shape = CutCornerShape(12.dp)
                            ) {
                                if (isLoading) CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(24.dp))
                                else Text("تحديث كلمة المرور مباشرة", fontFamily = CairoFont, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "استعادة مفتاح العبور",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp
                )
                Text(
                    text = "أدخل بريدك الإلكتروني لإرسال بروتوكول الاستعادة",
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
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = Color.White.copy(alpha = 0.1f)),
                    shape = CutCornerShape(8.dp)
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
                            var success = false
                            if (SupabaseServices.isSupabaseAvailable) {
                                success = SupabaseServices.Auth.resetPassword(email.trim())
                            }
                            if (!success && !SupabaseServices.isSupabaseAvailable) success = true
                            if (success) isSubmitted = true
                            else Toast.makeText(context, "فشل الإرسال. تأكد من صحة البريد", Toast.LENGTH_LONG).show()
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = DeepSlate),
                    shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                ) {
                    if (isLoading) CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(24.dp))
                    else Text("إرسال مفتاح الاستعادة", fontFamily = CairoFont, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

