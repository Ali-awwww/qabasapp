package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * زر تسجيل الدخول الموحد عبر Google بتصميم Cybernetic Glassmorphic HUD
 */
@Composable
fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    onConfigureClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    text: String = "المتابعة بحساب Google // ONE-TAP SSO"
) {
    val primaryColor = qabasPrimary()
    val isNeon = ThemeManager.currentPreset.collectAsState().value.isNeon

    val borderBrush = Brush.horizontalGradient(
        listOf(
            Color(0xFF4285F4),
            Color(0xFF34A853),
            Color(0xFFFBBC05),
            Color(0xFFEA4335)
        )
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .bouncingClickable(enabled = !isLoading, onClick = onClick),
            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.85f),
            border = BorderStroke(1.2.dp, borderBrush)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Subtle glow on hover/background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF4285F4).copy(alpha = 0.08f),
                                    Color.Transparent,
                                    Color(0xFF34A853).copy(alpha = 0.08f)
                                )
                            )
                        )
                )

                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4285F4),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "جاري المصادقة عبر Google...",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Google Icon
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Logo",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Labels
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = text,
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "SUPABASE AUTH // CREDENTIAL MANAGER",
                                color = Color(0xFF94A3B8),
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        // Arrow Indicator
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Sub-action for developer or manual setup
        if (onConfigureClick != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .clip(CutCornerShape(4.dp))
                    .clickable { onConfigureClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = primaryColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "دليل تفعيل Google OAuth في Supabase // GUIDE & CONFIG",
                    color = primaryColor.copy(alpha = 0.75f),
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * نافذة HUD متطورة تشرح وتسمح بتفعيل وتهيئة تسجيل الدخول عبر Google في Supabase
 */
@Composable
fun GoogleSupabaseSetupDialog(
    onDismiss: () -> Unit,
    onTestSignIn: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    var activeTab by remember { mutableStateOf(0) } // 0: Supabase Guide, 1: Google Cloud, 2: App Key Config
    var currentClientId by remember { mutableStateOf(GoogleAuthHelper.getWebClientId(context)) }
    var isSavingKey by remember { mutableStateOf(false) }

    val callbackUrl = GoogleAuthHelper.SUPABASE_GOOGLE_CALLBACK_URL
    val isConfigured = GoogleAuthHelper.isWebClientIdConfigured(context)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = CutCornerShape(16.dp),
                color = DeepSlate,
                border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.6f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HudCornerDecor(
                        modifier = Modifier.fillMaxSize(),
                        color = primaryColor.copy(alpha = 0.3f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                if (isConfigured) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "بروتوكول تهيئة GOOGLE AUTH // SUPABASE",
                                        color = primaryColor,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = "تفعيل تسجيل الدخول بزر Google الموحد",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.05f), CutCornerShape(6.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Status Badge Card
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = CutCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isConfigured) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isConfigured) Color(0xFF10B981) else Color(0xFFF59E0B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isConfigured) "معرف العميل (Client ID) مُهيأ وجاهز" else "معرف Google Web Client ID غير مدخل بعد",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = if (isConfigured) currentClientId.take(28) + "..." else "يحتاج إلى ربط Supabase بـ Google Cloud",
                                            color = Color(0xFF94A3B8),
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                if (isConfigured) {
                                    Button(
                                        onClick = onTestSignIn,
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                        shape = CutCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "تجربة الآن",
                                            color = DeepSlate,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "1. إعداد Supabase" to 0,
                                "2. Google Cloud" to 1,
                                "3. إدخال المعرف" to 2
                            ).forEach { (title, index) ->
                                val isSelected = activeTab == index
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { activeTab = index },
                                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                    color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color(0xFF151B2B),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) primaryColor else Color.White.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) primaryColor else Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Tab Content
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A).copy(alpha = 0.6f), CutCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), CutCornerShape(10.dp))
                                .padding(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                when (activeTab) {
                                    0 -> {
                                        // Supabase Steps
                                        Text(
                                            text = "خطوات تفعيل موزع Google في Supabase:",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        StepItem(
                                            number = "1",
                                            title = "افتح لوحة تحكم Supabase",
                                            description = "توجه إلى مشروعه في Supabase عبر الرابط: supabase.com/dashboard/project/aivmwovrbdzcyrjwubon",
                                            actionLabel = "فتح Supabase Dashboard",
                                            onAction = {
                                                runCatching {
                                                    val intent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://supabase.com/dashboard/project/aivmwovrbdzcyrjwubon/auth/providers")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            }
                                        )

                                        StepItem(
                                            number = "2",
                                            title = "انتقل إلى Authentication > Providers",
                                            description = "من القائمة الجانبية اختر Authentication ثم Providers، وابحث عن موزع Google."
                                        )

                                        StepItem(
                                            number = "3",
                                            title = "تفعيل Google Provider",
                                            description = "قم بتفعيل مفتاح (Enable Google provider) إلى وضع التشغيل ON."
                                        )

                                        StepItem(
                                            number = "4",
                                            title = "إدخال بيانات الاعتماد والحفظ",
                                            description = "ألصق Client ID و Client Secret اللذين استخرجتهما من Google Cloud، ثم اضغط Save."
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Redirect URL Box
                                        Text(
                                            text = "رابط إعادة التوجيه المطلوب لـ Google Cloud (Redirect URI):",
                                            color = accentColor,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            color = Color(0xFF1E293B),
                                            shape = CutCornerShape(6.dp),
                                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = callbackUrl,
                                                    color = Color.White,
                                                    fontFamily = RobotoMonoFont,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(callbackUrl))
                                                        Toast.makeText(context, "تم نسخ الرابط بنجاح!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "نسخ",
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    1 -> {
                                        // Google Cloud Steps
                                        Text(
                                            text = "خطوات استخراج المعرف من Google Cloud Console:",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        StepItem(
                                            number = "1",
                                            title = "الدخول لـ Google Cloud Console",
                                            description = "ادخل إلى console.cloud.google.com وأنشئ مشروعاً أو اختر مشروعك الحالي.",
                                            actionLabel = "فتح Google Cloud Console",
                                            onAction = {
                                                runCatching {
                                                    val intent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://console.cloud.google.com/apis/credentials")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            }
                                        )

                                        StepItem(
                                            number = "2",
                                            title = "تهيئة شاشة الموافقة (OAuth Consent Screen)",
                                            description = "اختر External، اكتب اسم التطبيق (قبس)، وأضف بريدك ونطاقات email, profile, openid."
                                        )

                                        StepItem(
                                            number = "3",
                                            title = "إنشاء معرّف Android (هام جداً)",
                                            description = "يجب إنشاء Android Client ID في نفس المشروع. ضع الحزمة: com.qabas.app وضع بصمة SHA-1 الخاصة بمفتاح التوقيع. بدون هذا لن يتعرف Google على طلبات التطبيق."
                                        )

                                        StepItem(
                                            number = "4",
                                            title = "إنشاء معرّف Web application",
                                            description = "من صفحة Credentials اضغط Create Credentials > OAuth client ID، واختر النوع: Web application. هذا هو المعرف الذي سنضعه في التطبيق."
                                        )

                                        StepItem(
                                            number = "5",
                                            title = "إضافة Authorized Redirect URI",
                                            description = "في حقل Authorized redirect URIs داخل الـ Web client أضف الرابط التالي:\n$callbackUrl"
                                        )

                                        StepItem(
                                            number = "6",
                                            title = "نسخ Web Client ID",
                                            description = "انسخ الـ Client ID الخاص بـ Web application (وليس Android) وضعه في التبويب التالي."
                                        )
                                    }

                                    2 -> {
                                        // Key Entry Tab
                                        Text(
                                            text = "إدخال وحفظ Google Web Client ID بالتطبيق:",
                                            color = Color.White,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "المعرف هو المفتاح الذي يسمح لمكتبة Credential Manager بفتح نافذة Google واختيار حسابك بدون إدخال كلمة مرور.",
                                            color = Color(0xFF94A3B8),
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Text(
                                            text = "اسم الحزمة (Package Name): com.qabas.app",
                                            color = accentColor,
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = currentClientId,
                                            onValueChange = { currentClientId = it },
                                            label = {
                                                Text("Google Web Client ID", fontFamily = RobotoMonoFont, fontSize = 11.sp)
                                            },
                                            placeholder = {
                                                Text("مثال: 123456789-xyz.apps.googleusercontent.com", color = Color.Gray, fontSize = 10.sp)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = primaryColor,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            shape = CutCornerShape(8.dp),
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val trimmed = currentClientId.trim()
                                                    if (trimmed.isBlank()) {
                                                        Toast.makeText(context, "الرجاء إدخال المعرف", Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    GoogleAuthHelper.saveWebClientId(context, trimmed)
                                                    Toast.makeText(context, "تم حفظ وتفعيل المعرف بنجاح!", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Save,
                                                    contentDescription = null,
                                                    tint = DeepSlate,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "حفظ المعرف",
                                                    color = DeepSlate,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = onTestSignIn,
                                                border = BorderStroke(1.dp, Color(0xFF4285F4)),
                                                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                                    contentDescription = null,
                                                    tint = Color.Unspecified,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "تجربة تسجيل الدخول",
                                                    color = Color.White,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Footer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PROTO: SUPABASE_AUTH_V2 // CREDENTIAL_MANAGER",
                                color = primaryColor.copy(alpha = 0.4f),
                                fontFamily = RobotoMonoFont,
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            )

                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = "إغلاق النافذة",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepItem(
    number: String,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val primaryColor = qabasPrimary()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(primaryColor.copy(alpha = 0.15f), CutCornerShape(4.dp))
                .border(0.8.dp, primaryColor.copy(alpha = 0.5f), CutCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = primaryColor,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = description,
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onAction() }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = actionLabel,
                        color = Color(0xFF4285F4),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
