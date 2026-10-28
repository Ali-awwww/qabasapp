package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*

/**
 * 🛰️ واجهة تعليق الاستوديو للمستخدمين العاديين
 * Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 *
 * تعرض رسالة توضيحية احترافية للمستخدم العادي تفيد بأن القسم قيد التطوير والصيانة،
 * مع توفير بوابة فحص آمنة لدخول المطورين والمشرفين (Developer Root Bypass).
 */
@Composable
fun StudioMaintenanceView(
    onBack: () -> Unit,
    onDeveloperAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    var showUnlockDialog by remember { mutableStateOf(false) }

    // Animations for HUD Tech Reticle
    val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line"
    )

    Scaffold(
        containerColor = Color(0xFF030712),
        bottomBar = bottomBar
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF030712),
                            Color(0xFF070E1E),
                            Color(0xFF030712)
                        )
                    )
                )
        ) {
        // Decorative Cyber HUD Grid Lines
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.12f)) {
            val gridStep = 40.dp.toPx()
            for (x in 0..(size.width / gridStep).toInt()) {
                drawLine(
                    color = primaryColor,
                    start = Offset(x * gridStep, 0f),
                    end = Offset(x * gridStep, size.height),
                    strokeWidth = 0.8f
                )
            }
            for (y in 0..(size.height / gridStep).toInt()) {
                drawLine(
                    color = primaryColor,
                    start = Offset(0f, y * gridStep),
                    end = Offset(size.width, y * gridStep),
                    strokeWidth = 0.8f
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Spatial HUD Status Pill
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Color(0xFF0B132B).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B).copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SUBSYSTEM // MAINTENANCE_ACTIVE",
                        color = Color(0xFFF59E0B),
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 2. Holographic HUD Reticle Ring with Lock/Engineering Icon
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .drawBehind {
                        // Outer segmented dashed circle
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.25f),
                            radius = size.minDimension / 2,
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), ringRotation)
                            )
                        )
                        // Inner ring
                        drawCircle(
                            color = accentColor.copy(alpha = 0.35f),
                            radius = (size.minDimension / 2) - 16.dp.toPx(),
                            style = Stroke(width = 1f)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Rotating Reticle Spokes
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(ringRotation)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.minDimension / 2
                        // Corner tick marks
                        drawLine(primaryColor.copy(alpha = 0.6f), Offset(center.x, 0f), Offset(center.x, 12.dp.toPx()), 2f)
                        drawLine(primaryColor.copy(alpha = 0.6f), Offset(center.x, size.height), Offset(center.x, size.height - 12.dp.toPx()), 2f)
                        drawLine(primaryColor.copy(alpha = 0.6f), Offset(0f, center.y), Offset(12.dp.toPx(), center.y), 2f)
                        drawLine(primaryColor.copy(alpha = 0.6f), Offset(size.width, center.y), Offset(size.width - 12.dp.toPx(), center.y), 2f)
                    }
                }

                // Core Glass Hexagon Icon Plate
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CutCornerShape(16.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.22f),
                                    Color(0xFF0F172A).copy(alpha = 0.85f)
                                )
                            )
                        )
                        .border(1.5.dp, primaryColor.copy(alpha = pulseAlpha), CutCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Construction,
                        contentDescription = "قيد الصيانة والتطوير",
                        tint = primaryColor,
                        modifier = Modifier.size(38.dp)
                    )

                    // Laser scanline effect
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * scanLineY
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    primaryColor.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 3. Cybernetic Glass Notice Card
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0xFF0C1322).copy(alpha = 0.82f),
                borderColor = primaryColor.copy(alpha = 0.35f),
                borderWidth = 1.2.dp
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = Translator.tr("قسم الاستوديو قيد الترقية والتطوير 🛠️"),
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = Translator.tr(
                            "عزيزنا صانع المحتوى، تم تعليق قسم استوديو قبس الموحّد مؤقتاً لترقية محركات الإنتاج وهندسة الذكاء الاصطناعي وإضافة أدوات مونتاج وصوتيات احترافية جديدة."
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = Translator.tr("سيعود القسم متاحاً لجميع المستخدمين فور اكتمال أعمال الترقية بإذن الله ✨"),
                        color = primaryColor,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Linear Minimalist Spatial Diagnostics HUD
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF030712).copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("SYSTEM MODULE:", color = Color(0xFF64748B), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                                Text("UNIFIED_CREATIVE_STUDIO", color = primaryColor, fontFamily = RobotoMonoFont, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("TARGET ENGINE:", color = Color(0xFF64748B), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                                Text("FLUX_8K_VOICE_PRO_v3", color = Color(0xFF38BDF8), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("GATEWAY STATUS:", color = Color(0xFF64748B), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                                Text("DEVELOPER_ONLY // RESTRICTED", color = Color(0xFFF59E0B), fontFamily = RobotoMonoFont, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Primary Return Button (Return Home)
            Button(
                onClick = onBack,
                shape = CutCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = DeepSlate
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Translator.tr("العودة إلى الصفحة الرئيسية"),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Subtle Developer Access Gate
            TextButton(
                onClick = { showUnlockDialog = true },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Translator.tr("دخول المطورين والمشرفين 🔑"),
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

    // Developer Passcode Dialog
    if (showUnlockDialog) {
        DeveloperUnlockDialog(
            onDismiss = { showUnlockDialog = false },
            onSuccess = {
                showUnlockDialog = false
                onDeveloperAuthenticated()
                Toast.makeText(context, Translator.tr("مرحباً بك! تم تفعيل وضع المطور بنجاح 👑"), Toast.LENGTH_SHORT).show()
            },
            primaryColor = primaryColor
        )
    }
}

/**
 * نافذة التحقق الآمن لدخول المطور / المشرف
 */
@Composable
fun DeveloperUnlockDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    primaryColor: Color
) {
    val context = LocalContext.current
    var passcode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val submitUnlock = {
        val trimmed = passcode.trim()
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val userEmail = prefs.getString("user_email", "") ?: ""

        // Validate developer credentials:
        // Restricted ONLY to registered owner accounts via CloudServices
        if (CloudServices.isOwnerAccount(userEmail)) {
            prefs.edit().apply {
                putBoolean("is_admin", true)
                putString("user_type", "مطور")
                apply()
            }
            keyboardController?.hide()
            onSuccess()
        } else {
            errorMessage = Translator.tr("رمز التحقق غير صحيح. مخصص لمطوري النظام فقط.")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xFF0F172A).copy(alpha = 0.95f),
            borderColor = primaryColor.copy(alpha = 0.5f),
            borderWidth = 1.5.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.15f))
                        .border(1.dp, primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = Translator.tr("التحقق من هوية المطور 🛡️"),
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )

                Text(
                    text = "DEVELOPER_ROOT_AUTHENTICATION",
                    color = primaryColor.copy(alpha = 0.7f),
                    fontFamily = RobotoMonoFont,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = Translator.tr("أدخل رمز المطور للمتابعة وفتح كافة أدوات الاستوديو:"),
                    color = Color.White.copy(alpha = 0.8f),
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = passcode,
                    onValueChange = {
                        passcode = it
                        errorMessage = null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = Translator.tr("رمز المطور (Developer PIN)"),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = primaryColor
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitUnlock() }),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFEF4444),
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(
                            text = Translator.tr("إلغاء"),
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = submitUnlock,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = DeepSlate
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(
                            text = Translator.tr("تأكيد"),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
