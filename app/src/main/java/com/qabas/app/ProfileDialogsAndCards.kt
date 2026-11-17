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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun ProfileChangePasswordDialog(
    userEmail: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    var newPw by remember { mutableStateOf("") }
    var confirmNewPw by remember { mutableStateOf("") }
    var isUpdatingPw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isUpdatingPw) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LockReset, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تغيير كلمة المرور", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("الحساب الحالي: $userEmail", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)

                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("كلمة المرور الجديدة", fontFamily = CairoFont) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = confirmNewPw,
                    onValueChange = { confirmNewPw = it },
                    label = { Text("تأكيد كلمة المرور الجديدة", fontFamily = CairoFont) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPw.length < 6) {
                        Toast.makeText(context, "كلمة المرور يجب ألا تقل عن 6 أحرف", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (newPw != confirmNewPw) {
                        Toast.makeText(context, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isUpdatingPw = true
                    coroutineScope.launch {
                        val success = runCatching {
                            SupabaseServices.isSupabaseAvailable &&
                                SupabaseServices.Auth.updatePassword(newPw.trim())
                        }.getOrDefault(false)
                        isUpdatingPw = false
                        if (success) {
                            onDismiss()
                            Toast.makeText(context, "تم تحديث كلمة المرور في Supabase بنجاح", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "تعذر تحديث كلمة المرور. تحقق من اتصال Supabase ومن تسجيل الدخول.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isUpdatingPw,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isUpdatingPw) {
                    CircularProgressIndicator(color = theme.onPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("تحديث كلمة السر", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUpdatingPw) {
                Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
            }
        },
        containerColor = theme.cardSurface
    )
}

@Composable
internal fun ProfileSwitchAccountDialog(
    displayName: String,
    userEmail: String,
    onDismiss: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    val coroutineScope = rememberCoroutineScope()
    val registeredAccounts = remember { LocalAccountRegistry.getAllAccounts(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تبديل الحساب", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الحساب النشط حالياً:", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                Surface(
                    color = theme.secondarySurface.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 14.sp)
                            Text(userEmail, color = theme.primary, fontFamily = RobotoMonoFont, fontSize = 11.sp)
                        }
                        Surface(color = theme.primary.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                            Text("نشط", color = theme.primary, fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }

                if (registeredAccounts.size > 1) {
                    Text(
                        "الحسابات المسجلة (${registeredAccounts.size - 1}):",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 220.dp)
                    ) {
                        registeredAccounts.filter { !it.email.equals(userEmail, ignoreCase = true) }.forEach { otherAcc ->
                            Surface(
                                onClick = {
                                    onDismiss()
                                    coroutineScope.launch {
                                        ActiveSessionManager.endLocalSession(context)
                                        prefs.edit().putString("saved_login_email", otherAcc.email).apply()
                                        Toast.makeText(context, "يرجى تسجيل الدخول للحساب: ${otherAcc.email}", Toast.LENGTH_LONG).show()
                                        onNavigateToLogin()
                                    }
                                },
                                color = theme.secondarySurface.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(otherAcc.name, color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(otherAcc.email, color = theme.textSecondary, fontFamily = RobotoMonoFont, fontSize = 10.5.sp)
                                    }
                                    Text("تبديل", color = theme.primary, fontSize = 12.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Text(
                    "أو يمكنك تسجيل الخروج للدخول بحساب آخر:",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    coroutineScope.launch {
                        ActiveSessionManager.endLocalSession(context)
                        onNavigateToLogin()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.destructive, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("تسجيل الخروج", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
            }
        },
        containerColor = theme.cardSurface
    )
}

@Composable
internal fun ProfileEditDialog(
    userEmail: String,
    currentName: String,
    currentTag: String,
    currentBio: String,
    currentAccountNumber: String,
    onDismiss: () -> Unit,
    onProfileUpdated: (name: String, tag: String, bio: String, accNum: String) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    val profilePrefs = remember(userEmail) { AccountScopedPreferences.forEmail(context, userEmail) }
    val coroutineScope = rememberCoroutineScope()

    var tempName by remember { mutableStateOf(currentName) }
    var tempTag by remember { mutableStateOf(currentTag) }
    var tempBio by remember { mutableStateOf(currentBio) }
    var tagError by remember { mutableStateOf<String?>(null) }
    var isCheckingTag by remember { mutableStateOf(false) }

    val activeAccountNumber = currentAccountNumber.takeIf { it.isNotBlank() }
        ?: UserIdentifierManager.getAccountNumber(context, userEmail).takeIf { it.isNotBlank() }
        ?: UserIdentifierManager.peekNextAccountNumber(context)

    AlertDialog(
        onDismissRequest = { if (!isCheckingTag) onDismiss() },
        title = {
            Text("تعديل الملف الشخصي", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "رقم الحساب المعتمد (ثابت وموثق سحابياً):",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp
                )
                Surface(
                    color = theme.secondarySurface.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = theme.warning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                UserIdentifierManager.formatAccountNumber(activeAccountNumber),
                                color = theme.warning,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Surface(color = theme.success.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                            Text("موثق", color = theme.success, fontSize = 10.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("اسم الحساب", fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = tempTag,
                    onValueChange = {
                        tempTag = it
                        tagError = null
                    },
                    label = { Text("المعرف الرقمي الفريد (@username)", fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = tagError != null,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                if (tagError != null) {
                    Text(
                        text = tagError!!,
                        color = theme.destructive,
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp
                    )
                }

                OutlinedTextField(
                    value = tempBio,
                    onValueChange = { tempBio = it },
                    label = { Text("النبذة التعريفية", fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isCheckingTag,
                onClick = {
                    val cleanId = UserIdentifierManager.normalize(tempTag)
                    if (cleanId.isBlank()) {
                        tagError = "يرجى إدخال معرّف صحيح"
                        return@Button
                    }
                    isCheckingTag = true
                    coroutineScope.launch {
                        val checkResult = UserIdentifierManager.checkCustomIdAvailability(
                            context = context,
                            customId = cleanId,
                            currentEmail = userEmail
                        )
                        when (checkResult) {
                            is UserIdentifierManager.CustomIdAvailabilityResult.Taken -> {
                                isCheckingTag = false
                                tagError = checkResult.reason
                            }
                            is UserIdentifierManager.CustomIdAvailabilityResult.Invalid -> {
                                isCheckingTag = false
                                tagError = checkResult.reason
                            }
                            is UserIdentifierManager.CustomIdAvailabilityResult.Available,
                            is UserIdentifierManager.CustomIdAvailabilityResult.CurrentUserOwnsIt -> {
                                val authUid = SupabaseServices.Auth.getCurrentUserId()
                                if (authUid.isNullOrBlank()) {
                                    isCheckingTag = false
                                    tagError = "يجب تسجيل الدخول إلى السحابة لتعديل ملفك الشخصي."
                                    return@launch
                                }
                                val sessionEmail = SupabaseServices.Auth.getCurrentUserEmail()?.trim()?.lowercase(Locale.ROOT)
                                val activeEmail = sessionEmail ?: userEmail
                                val normalizedCleanId = UserIdentifierManager.normalize(cleanId)
                                val nextDisplayName = tempName.ifBlank { "مستخدم قبس" }
                                val profileSaveResult = SupabaseServices.Database.saveOwnProfile(
                                    id = authUid,
                                    name = nextDisplayName,
                                    email = activeEmail,
                                    customId = normalizedCleanId
                                )
                                if (profileSaveResult.isFailure) {
                                    val errorText = profileSaveResult.exceptionOrNull()?.message.orEmpty()
                                    val normalizedError = errorText.lowercase(Locale.ROOT)
                                    tagError = when {
                                        "users_custom_id_lower_unique" in normalizedError ||
                                            "duplicate key" in normalizedError ||
                                            "23505" in normalizedError ->
                                            "هذا المعرّف مستخدم بالفعل. اختر معرّفاً آخر."
                                        "requested username is invalid" in normalizedError ||
                                            "22023" in normalizedError ->
                                            "صيغة المعرّف غير مقبولة. استخدم أحرفاً إنجليزية وأرقاماً أو _ أو -."
                                        "42501" in normalizedError ||
                                            "permission denied" in normalizedError ->
                                            "رفضت صلاحيات الحساب التحديث. أعد تسجيل الدخول."
                                        "انتهت جلسة الدخول" in errorText ->
                                            "انتهت جلسة الدخول. سجّل الدخول مجدداً."
                                        else -> "تعذر حفظ بيانات الملف في السحابة. تحقق من الاتصال."
                                    }
                                    isCheckingTag = false
                                    return@launch
                                }

                                val cloudUser = profileSaveResult.getOrNull()
                                if (cloudUser == null || cloudUser.id != authUid) {
                                    isCheckingTag = false
                                    tagError = "لم تؤكد السحابة حفظ البيانات."
                                    return@launch
                                }

                                val profileMirrorSynced = SupabaseServices.Profiles.syncUserProfile(
                                    userId = authUid,
                                    displayName = cloudUser.name?.takeIf { it.isNotBlank() } ?: nextDisplayName,
                                    email = activeEmail,
                                    avatarUrl = SupabaseServices.Auth.getCurrentUserMeta("avatar_url")
                                        ?: SupabaseServices.Auth.getCurrentUserMeta("picture")
                                )
                                val bioSaved = SupabaseServices.UserSettings.saveUserBio(authUid, tempBio)
                                val resolvedAccNumber = cloudUser.accountNumber?.takeIf { it.isNotBlank() }
                                    ?: UserIdentifierManager.getAccountNumber(context, activeEmail)
                                    ?: "QBS-0001"

                                val finalName = cloudUser.name?.takeIf { it.isNotBlank() } ?: nextDisplayName
                                val finalTag = UserIdentifierManager.formatDisplay(cloudUser.customId!!)
                                UserIdentifierManager.saveCustomId(context, cloudUser.customId, activeEmail)
                                UserIdentifierManager.saveAccountNumber(context, activeEmail, resolvedAccNumber)

                                profilePrefs.edit().putString("user_name", finalName).putString("user_bio", tempBio).apply()
                                prefs.edit()
                                    .putString("user_id", authUid)
                                    .putString("user_display_name", finalName)
                                    .putString("user_name", finalName)
                                    .putString("user_tag", finalTag)
                                    .putString("user_custom_id", cloudUser.customId)
                                    .putString("user_account_number", cloudUser.accountNumber)
                                    .apply()
                                AccountIdentityManager.refresh(context)

                                isCheckingTag = false
                                Toast.makeText(
                                    context,
                                    when {
                                        !profileMirrorSynced -> "تم حفظ الاسم في الحساب، لكن تعذرت مزامنة ملف العرض السحابي."
                                        bioSaved -> "تم حفظ الملف الشخصي بنجاح"
                                        else -> "تم حفظ الاسم والمعرّف، لكن تعذرت مزامنة النبذة."
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                                onProfileUpdated(finalName, finalTag, tempBio, resolvedAccNumber)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isCheckingTag) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = theme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("جاري الحفظ...", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                } else {
                    Text("حفظ التغييرات", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isCheckingTag) onDismiss() }) {
                Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
            }
        },
        containerColor = theme.cardSurface
    )
}

@Composable
internal fun ProfileAvatarPickerDialog(
    selectedPresetAvatar: Int,
    hasCustomUri: Boolean,
    onPickFromGallery: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("تغيير الصورة الشخصية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ApplePrimaryButton(
                    text = "اختيار صورة من المعرض",
                    icon = Icons.Default.PhotoLibrary,
                    onClick = onPickFromGallery
                )
                HorizontalDivider(color = theme.separator.copy(alpha = 0.45f))
                Text("أو اختر رمزاً دعوياً:", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.5.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val presets = listOf(
                        0 to Icons.Default.Person,
                        1 to Icons.Default.AutoAwesome,
                        2 to Icons.Default.MovieFilter,
                        3 to Icons.Default.MenuBook,
                        4 to Icons.Default.Psychology
                    )
                    presets.forEach { (index, icon) ->
                        val isSelected = selectedPresetAvatar == index && !hasCustomUri
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) theme.primary else theme.secondarySurface)
                                .border(1.dp, if (isSelected) theme.primary else theme.separator, CircleShape)
                                .clickable { onSelectPreset(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = "رمز $index",
                                tint = if (isSelected) theme.onPrimary else theme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = theme.textSecondary, fontFamily = CairoFont)
            }
        },
        containerColor = theme.cardSurface
    )
}

@Composable
internal fun SocialHandleEditDialog(
    account: SocialPlatformAccount,
    handleText: String,
    onHandleChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("تعديل معرف ${account.name}", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل اسم المستخدم على منصة ${account.name}:", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                OutlinedTextField(
                    value = handleText,
                    onValueChange = onHandleChange,
                    placeholder = { Text("@username", fontFamily = RobotoMonoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حفظ", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
            }
        },
        containerColor = theme.cardSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentInfoBottomSheet(
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.cardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = theme.warning, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("دليل دوري صنّاع الأثر", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text("كيف تجمع النقاط؟", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            PointRuleItem("إنشاء مشروع دعوي جديد", "50 نقطة")
            PointRuleItem("نشر فيديو على منصات التواصل", "20 نقطة")
            PointRuleItem("تحقيق 1000 مشاهدة", "100 نقطة")
            PointRuleItem("التفاعل مع مجتمع قبس", "10 نقاط")

            Spacer(modifier = Modifier.height(20.dp))

            Text("الرتب والمزايا", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            TierInfoItem("البرونزية", "0 - 499 نقطة", "بداية الرحلة والظهور الأولي")
            TierInfoItem("الفضية", "500 - 1499 نقطة", "مضاعف نقاط 1.2x وأولوية معالجة")
            TierInfoItem("الذهبية", "1500 - 3499 نقطة", "إطار ذهبي ومضاعف 1.5x")
            TierInfoItem("الماسية", "3500 - 6999 نقطة", "توليد فوري غير محدود للمشاهد")
            TierInfoItem("السفير", "7000 - 11999 نقطة", "شارة توثيق ومزايا تجريبية")
            TierInfoItem("الأسطورية", "12000+ نقطة", "تخليد في لوحة الشرف الخالدة")

            Spacer(modifier = Modifier.height(22.dp))

            ApplePrimaryButton(text = "حسناً", onClick = onDismiss)
        }
    }
}

@Composable
fun PointRuleItem(title: String, points: String) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 13.sp)
        Text(points, color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun TierInfoItem(tier: String, range: String, perk: String) {
    val theme = QabasTheme.current
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tier, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(range, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
        }
        Text(perk, color = theme.primary, fontFamily = NotoSansFont, fontSize = 11.5.sp)
    }
}

@Composable
fun TournamentBadgeCard(
    badge: TournamentTierBadge,
    userPoints: Int,
    userRank: Int,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    val isUnlocked = badge.isUnlocked
    val isCurrent = badge.isCurrentTier

    AppleCard(
        cornerRadius = 14.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isUnlocked) badge.primaryColor.copy(alpha = 0.14f) else theme.secondarySurface)
                    .border(
                        width = if (isCurrent) 1.5.dp else 1.dp,
                        color = if (isUnlocked) badge.primaryColor else theme.separator,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isUnlocked) badge.icon else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isUnlocked) badge.primaryColor else theme.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                badge.title,
                color = if (isUnlocked) theme.textPrimary else theme.textSecondary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )

            Text(
                badge.tierTitle,
                color = if (isUnlocked) badge.primaryColor else theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 10.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isCurrent -> badge.primaryColor.copy(alpha = 0.16f)
                    isUnlocked -> theme.success.copy(alpha = 0.14f)
                    else -> theme.secondarySurface
                }
            ) {
                Text(
                    text = when {
                        isCurrent -> "الرتبة الحالية"
                        isUnlocked -> "مفتوح ✓"
                        badge.rankRequired != null -> "الترتيب #${badge.rankRequired}"
                        else -> "${badge.pointsRequired} نقطة"
                    },
                    color = when {
                        isCurrent -> badge.primaryColor
                        isUnlocked -> theme.success
                        else -> theme.textSecondary
                    },
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun TournamentBadgeDetailDialog(
    badge: TournamentTierBadge,
    userPoints: Int,
    userRank: Int,
    onDismiss: () -> Unit,
    onNavigateToLeague: () -> Unit
) {
    val theme = QabasTheme.current
    val isUnlocked = badge.isUnlocked
    val isCurrent = badge.isCurrentTier

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isUnlocked) badge.primaryColor.copy(alpha = 0.15f) else theme.secondarySurface)
                        .border(2.dp, if (isUnlocked) badge.primaryColor else theme.separator, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isUnlocked) badge.icon else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isUnlocked) badge.primaryColor else theme.textSecondary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "${badge.title} ${badge.emoji}",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    badge.tierTitle,
                    color = badge.primaryColor,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isCurrent -> badge.primaryColor.copy(alpha = 0.14f)
                        isUnlocked -> theme.success.copy(alpha = 0.14f)
                        else -> theme.secondarySurface
                    }
                ) {
                    Text(
                        text = when {
                            isCurrent -> "رتبتك الحالية في الدوري"
                            isUnlocked -> "تم استحقاق هذا الوسام"
                            else -> "مقفل — واصل التقدم لفتحه"
                        },
                        color = when {
                            isCurrent -> badge.primaryColor
                            isUnlocked -> theme.success
                            else -> theme.textSecondary
                        },
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.secondarySurface.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("عن الوسام", color = theme.textSecondary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(badge.description, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.5.sp, lineHeight = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.primary.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("المزايا الممنوحة", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(badge.perk, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.5.sp, lineHeight = 18.sp)
                    }
                }

                if (!isUnlocked) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = theme.secondarySurface.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (badge.rankRequired != null) {
                                Text("متطلب الترتيب: #${badge.rankRequired} أو أفضل (ترتيبك: #$userRank)", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                            } else {
                                val remaining = (badge.pointsRequired - userPoints).coerceAtLeast(0)
                                val fraction = (userPoints.toFloat() / badge.pointsRequired.coerceAtLeast(1).toFloat()).coerceIn(0.05f, 1f)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("التقدم", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 11.5.sp)
                                    Text("$userPoints / ${badge.pointsRequired}", color = theme.primary, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = theme.primary,
                                    trackColor = theme.cardSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("متبقي $remaining نقطة", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onNavigateToLeague,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("صالة البطولة", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = theme.textSecondary, fontFamily = CairoFont)
            }
        },
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun SocialPlatformItemCard(
    account: SocialPlatformAccount,
    onToggleConnect: () -> Unit,
    onEditHandle: () -> Unit,
    onOpenProfile: () -> Unit = {}
) {
    val theme = QabasTheme.current
    val platformColor = when (account.id) {
        "youtube" -> Color(0xFFD92D20)
        "tiktok" -> theme.primary
        "instagram" -> Color(0xFFE1306C)
        "twitter" -> Color(0xFF1DA1F2)
        else -> Color(0xFF1877F2)
    }

    val platformIcon: ImageVector = when (account.id) {
        "youtube" -> Icons.Default.OndemandVideo
        "tiktok" -> Icons.Default.MusicVideo
        "instagram" -> Icons.Default.CameraAlt
        "twitter" -> Icons.Default.Tag
        else -> Icons.Default.Share
    }

    AppleCard(cornerRadius = 14.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(platformColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(platformIcon, contentDescription = null, tint = platformColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(account.name, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Text(
                        if (account.isConnected) account.handle else "غير مربوط",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (account.isConnected) {
                    IconButton(onClick = onOpenProfile, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "فتح الحساب", tint = platformColor, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onEditHandle, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = theme.primary, modifier = Modifier.size(16.dp))
                    }
                }

                Surface(
                    onClick = onToggleConnect,
                    color = if (account.isConnected) platformColor.copy(alpha = 0.14f) else theme.secondarySurface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (account.isConnected) "مربوط" else "ربط",
                        color = if (account.isConnected) platformColor else theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.RankHighlightCard(label: String, value: String, icon: ImageVector, accent: Color) {
    val theme = QabasTheme.current
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        color = theme.secondarySurface.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(label, color = theme.textSecondary, fontFamily = CairoFont, fontSize = 10.5.sp)
                Text(value, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun HudCornerDecor(modifier: Modifier = Modifier) {
    // Kept as no-op signature compatibility helper for Apple minimal style
    Spacer(modifier = modifier.size(0.dp))
}

fun formatStatNumber(num: Int): String {
    return when {
        num >= 1_000_000 -> String.format(Locale.US, "%.1fM", num / 1_000_000.0)
        num >= 1_000 -> String.format(Locale.US, "%.1fK", num / 1_000.0)
        else -> num.toString()
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector, color: Color) {
    val theme = QabasTheme.current
    AppleCard(modifier = modifier, cornerRadius = 14.dp) {
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp)
        }
    }
}

@Composable
fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp
            )
        }
        content()
    }
}

@Composable
fun ProfileActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    if (isPrimary) {
        ApplePrimaryButton(text = label, icon = icon, onClick = onClick, modifier = modifier)
    } else {
        AppleSecondaryButton(text = label, icon = icon, onClick = onClick, modifier = modifier)
    }
}
