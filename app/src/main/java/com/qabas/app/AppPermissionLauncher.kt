package com.qabas.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qabas.app.ui.theme.*

/**
 * 🛡️ مدير أذونات وصلاحيات العمل بالخلفية والأذان الشامل
 * (Comprehensive Background & Azan Permission Handler)
 * بتصميم: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 */
@Composable
fun AppStartupPermissionHandler(
    onOpenPermissionsHub: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("qabas_permissions_prefs", Context.MODE_PRIVATE) }
    var hasRequestedInitially by remember { mutableStateOf(prefs.getBoolean("initial_permissions_requested", false)) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Live permission states
    var isBatteryIgnored by remember { mutableStateOf(BackgroundPermissionManager.isBatteryOptimizationIgnored(context)) }
    var canExactAlarm by remember { mutableStateOf(BackgroundPermissionManager.canScheduleExactAlarms(context)) }
    var isNotificationGranted by remember { mutableStateOf(BackgroundPermissionManager.areNotificationsEnabled(context)) }
    var isMicGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var isCameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    fun refreshStates() {
        isBatteryIgnored = BackgroundPermissionManager.isBatteryOptimizationIgnored(context)
        canExactAlarm = BackgroundPermissionManager.canScheduleExactAlarms(context)
        isNotificationGranted = BackgroundPermissionManager.areNotificationsEnabled(context)
        isMicGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        isCameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    // Auto-refresh when user returns to app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        prefs.edit().putBoolean("initial_permissions_requested", true).apply()
        hasRequestedInitially = true
        refreshStates()
        BackgroundPermissionManager.refreshBackgroundSchedulers(context)

        // If battery or exact alarm is missing, show informative background guidance
        if (!BackgroundPermissionManager.isBatteryOptimizationIgnored(context) ||
            !BackgroundPermissionManager.canScheduleExactAlarms(context)
        ) {
            showPermissionDialog = true
        }
    }

    fun requestRuntimePermissions() {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationGranted) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!isMicGranted) list.add(Manifest.permission.RECORD_AUDIO)
        if (!isCameraGranted) list.add(Manifest.permission.CAMERA)

        if (list.isNotEmpty()) {
            permissionLauncher.launch(list.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        refreshStates()
        if (!hasRequestedInitially) {
            requestRuntimePermissions()
        } else {
            val anyVitalMissing = !isBatteryIgnored || !canExactAlarm || !isNotificationGranted
            if (anyVitalMissing && !prefs.getBoolean("bg_permission_dismissed", false)) {
                showPermissionDialog = true
            }
        }
    }

    if (showPermissionDialog) {
        BackgroundPermissionsDialog(
            isBatteryIgnored = isBatteryIgnored,
            canExactAlarm = canExactAlarm,
            isNotificationGranted = isNotificationGranted,
            isMicGranted = isMicGranted,
            isCameraGranted = isCameraGranted,
            onDismiss = {
                showPermissionDialog = false
                prefs.edit().putBoolean("bg_permission_dismissed", true).apply()
            },
            onRequestBattery = {
                BackgroundPermissionManager.requestIgnoreBatteryOptimizations(context)
                refreshStates()
            },
            onRequestExactAlarm = {
                BackgroundPermissionManager.requestScheduleExactAlarm(context)
                refreshStates()
            },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    BackgroundPermissionManager.openNotificationSettings(context)
                }
                refreshStates()
            },
            onRequestMic = {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                refreshStates()
            },
            onRequestCamera = {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                refreshStates()
            },
            onOpenFullHub = {
                showPermissionDialog = false
                onOpenPermissionsHub()
            },
            onGrantAll = {
                requestRuntimePermissions()
                BackgroundPermissionManager.requestIgnoreBatteryOptimizations(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExactAlarm) {
                    BackgroundPermissionManager.requestScheduleExactAlarm(context)
                }
                BackgroundPermissionManager.refreshBackgroundSchedulers(context)
                Toast.makeText(context, "تم تفعيل درع حماية الأذان واستوديو الإنتاج 🚀", Toast.LENGTH_SHORT).show()
                showPermissionDialog = false
            }
        )
    }
}

/**
 * نافذة حوارية فضائية لشرح أذونات العمل بالخلفية والأذان والإنتاج
 * بنمط Cybernetic Glassmorphic Spatial HUD
 */
@Composable
fun BackgroundPermissionsDialog(
    isBatteryIgnored: Boolean,
    canExactAlarm: Boolean,
    isNotificationGranted: Boolean,
    isMicGranted: Boolean = true,
    isCameraGranted: Boolean = true,
    onDismiss: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestMic: () -> Unit = {},
    onRequestCamera: () -> Unit = {},
    onOpenFullHub: () -> Unit = {},
    onGrantAll: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF080D1A),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .border(
                1.5.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.5f),
                        GoldPrimary.copy(alpha = 0.3f),
                        Color(0xFF1E293B).copy(alpha = 0.6f)
                    )
                ),
                RoundedCornerShape(24.dp)
            ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "درع الأذونات وحماية النظام",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        "SPATIAL HUD • PRAYER & STUDIO SENTINEL",
                        fontFamily = NotoSansFont,
                        color = Color(0xFF00E5FF),
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "لضمان دقة رفع الأذان بالثانية، حماية التنبيهات من تجميد النظام، وتشغيل أدوات استوديو الصوت والفيديو بكفاءة، يُرجى تفعيل الدروع التالية:",
                    color = Color(0xFFCBD5E1),
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )

                // 1. إذن الإشعارات
                PermissionStatusCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "إشعارات الأذان والصلوات",
                    desc = "لظهور تنبيهات دخول وقت الصلاة والأذكار على شاشة القفل",
                    isGranted = isNotificationGranted,
                    accentColor = Color(0xFF38BDF8),
                    onAction = onRequestNotifications
                )

                // 2. استثناء تحسين البطارية (Doze mode)
                PermissionStatusCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "درع خلفية الأذان (استثناء البطارية)",
                    desc = "لمنع أندرويد من إيقاف أو تأخير الأذان أثناء نوم وخمول الجهاز",
                    isGranted = isBatteryIgnored,
                    accentColor = Color(0xFF00E5FF),
                    onAction = onRequestBattery
                )

                // 3. المنبهات الدقيقة (Exact Alarms)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionStatusCard(
                        icon = Icons.Default.AlarmOn,
                        title = "المنبهات الدقيقة في الوقت المحدد",
                        desc = "لرفع الأذان في نفس اللحظة والثانية المحددة للصلوات الخمس",
                        isGranted = canExactAlarm,
                        accentColor = Color(0xFF10B981),
                        onAction = onRequestExactAlarm
                    )
                }

                // 4. ميكروفون استوديو الصوتيات إن كان معطلاً
                if (!isMicGranted) {
                    PermissionStatusCard(
                        icon = Icons.Default.Mic,
                        title = "ميكروفون استوديو التلاوة والصوت",
                        desc = "لتسجيل التلاوات والمقاطع الصوتية داخل الاستوديو",
                        isGranted = false,
                        accentColor = Color(0xFFF59E0B),
                        onAction = onRequestMic
                    )
                }

                // 5. التشغيل التلقائي (لأجهزة شاومي وهواوي وسامسونج)
                Surface(
                    color = Color(0xFF101726).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { BackgroundPermissionManager.openManufacturerAutostartSettings(context) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("إعدادات التشغيل التلقائي للهاتف", color = Color.White, fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Text("لهواتف شاومي، هواوي، سامسونج وأوبو", color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 9.5.sp)
                            }
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantAll,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تفعيل جميع الصلاحيات والدروع 🚀",
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("المتابعة لاحقاً", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.5.sp)
                }
                TextButton(onClick = onOpenFullHub) {
                    Text("مركز الأذونات الكامل 🛸", color = Color(0xFF00E5FF), fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun PermissionStatusCard(
    icon: ImageVector,
    title: String,
    desc: String,
    isGranted: Boolean,
    accentColor: Color,
    onAction: () -> Unit
) {
    Surface(
        color = Color(0xFF0E1524),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isGranted) Color(0xFF10B981).copy(alpha = 0.5f) else accentColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(desc, color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 9.5.sp, lineHeight = 13.sp)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("ممنوح", color = Color(0xFF10B981), fontSize = 10.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("تفعيل ⚡", color = DeepSlate, fontSize = 10.5.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
