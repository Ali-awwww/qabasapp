package com.qabas.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleListRow
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme

/**
 * واجهة الصيانة الاختيارية بأسلوب Apple HIG النظيف للوضعين الفاتح والداكن.
 */
@Composable
fun StudioMaintenanceView(
    onBack: () -> Unit,
    onDeveloperAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    var showUnlockDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = Translator.tr("حالة الاستوديو"),
                onBack = onBack
            )
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(theme.warning.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = Translator.tr("قيد الصيانة"),
                    tint = theme.warning,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = Translator.tr("بعض وحدات الاستوديو قيد التحديث"),
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = Translator.tr(
                    "نعمل على تحسين محركات الإنتاج والذكاء الاصطناعي لضمان أعلى استقرار وجودة للتصدير."
                ),
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            AppleCard {
                AppleListRow(
                    title = Translator.tr("الوحدة"),
                    trailingText = Translator.tr("استوديو خادم المسلم الموحّد"),
                    showChevron = false
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = theme.separator.copy(alpha = 0.45f)
                )
                AppleListRow(
                    title = Translator.tr("الحالة"),
                    trailingText = Translator.tr("صيانة مجدولة"),
                    showChevron = false
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ApplePrimaryButton(
                text = Translator.tr("العودة إلى الرئيسية"),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack
            )

            Spacer(modifier = Modifier.height(10.dp))

            AppleSecondaryButton(
                text = Translator.tr("التحقق من صلاحية المطور"),
                icon = Icons.Default.VpnKey,
                onClick = { showUnlockDialog = true }
            )
        }
    }

    if (showUnlockDialog) {
        DeveloperUnlockDialog(
            onDismiss = { showUnlockDialog = false },
            onSuccess = {
                showUnlockDialog = false
                onDeveloperAuthenticated()
                Toast.makeText(context, Translator.tr("تم التحقق من صلاحية المطور بنجاح"), Toast.LENGTH_SHORT).show()
            },
            primaryColor = theme.primary
        )
    }
}

/**
 * نافذة التحقق الفعلي من هوية المطور عبر جلسة Supabase الموثقة.
 */
@Composable
fun DeveloperUnlockDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    primaryColor: Color
) {
    val theme = QabasTheme.current
    val authEmail = remember { SupabaseServices.Auth.getCurrentUserEmail() }
    val isSignedIn = remember { SupabaseServices.Auth.getCurrentUserId() != null }
    val isAuthorizedOwner = remember(authEmail, isSignedIn) {
        isSignedIn && CloudServices.isOwnerAccount(authEmail)
    }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        AppleCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            cornerRadius = 20.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = Translator.tr("التحقق من صلاحية المطور"),
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isAuthorizedOwner -> Translator.tr("الحساب الحالي موثّق كحساب مالك/مطور: ${authEmail.orEmpty()}")
                        isSignedIn -> Translator.tr("الحساب المسجّل حالياً (${authEmail ?: "غير معروف"}) لا يملك صلاحية المطور.")
                        else -> Translator.tr("يرجى تسجيل الدخول بحساب المطور المعتمد عبر Supabase أولاً.")
                    },
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = theme.destructive,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppleSecondaryButton(
                        text = Translator.tr("إغلاق"),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        tint = theme.textSecondary
                    )
                    ApplePrimaryButton(
                        text = Translator.tr("متابعة"),
                        enabled = isAuthorizedOwner,
                        onClick = {
                            if (isAuthorizedOwner) {
                                onSuccess()
                            } else {
                                statusMessage = Translator.tr("الحساب الحالي لا يملك صلاحية المطور.")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
