package com.qabas.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qabas.app.ui.theme.*

/**
 * طلب جميع الصلاحيات تلقائياً عند دخول التطبيق:
 * - الإشعارات (POST_NOTIFICATIONS) للتذكيرات القرآنية والأذكار
 * - الميكروفون (RECORD_AUDIO) لاستوديو الصوتيات والتلاوة
 * - الكاميرا (CAMERA) لاستوديو الفيديو والتصوير
 * بتصميم: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 */
@Composable
fun AppPermissionsEntryLauncher(
    onPermissionsHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    var hasRequestedOnStartup by remember {
        mutableStateOf(prefs.getBoolean("has_prompted_all_permissions_v1", false))
    }
    var showExplanationDialog by remember { mutableStateOf(false) }

    val permissionsToRequest = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
        }.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultsMap ->
        prefs.edit().putBoolean("has_prompted_all_permissions_v1", true).apply()
        hasRequestedOnStartup = true
        onPermissionsHandled()
    }

    LaunchedEffect(Unit) {
        if (!hasRequestedOnStartup) {
            val ungranted = permissionsToRequest.filter { perm ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
            }
            if (ungranted.isNotEmpty()) {
                launcher.launch(permissionsToRequest)
            } else {
                prefs.edit().putBoolean("has_prompted_all_permissions_v1", true).apply()
            }
        }
    }

    if (showExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showExplanationDialog = false },
            containerColor = Color(0xFF080D1A),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.5f),
                        Color(0xFF1E293B).copy(alpha = 0.5f)
                    )
                ),
                RoundedCornerShape(22.dp)
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("صلاحيات استوديو قبس", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("SPATIAL PERMISSION HUD", color = Color(0xFF00E5FF), fontFamily = NotoSansFont, fontSize = 9.sp, letterSpacing = 0.5.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "للحصول على أفضل تجربة واستفادة كاملة من أدوات الاستوديو، يحتاج التطبيق إلى الصلاحيات التالية:",
                        color = Color(0xFFCBD5E1),
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                    PermissionExplItem(
                        icon = Icons.Default.NotificationsActive,
                        title = "الإشعارات الذكية والمنارة",
                        desc = "لتنبيهات الأذكار وأوقات الصلاة ومتابعة الرندرة بالخلفية",
                        accentColor = Color(0xFFF59E0B)
                    )
                    PermissionExplItem(
                        icon = Icons.Default.Mic,
                        title = "تسجيل الصوتيات والتلاوة",
                        desc = "لتسجيل التلاوات والمقاطع في استوديو الصوتيات الاحترافي",
                        accentColor = Color(0xFF38BDF8)
                    )
                    PermissionExplItem(
                        icon = Icons.Default.CameraAlt,
                        title = "الكاميرا والتصوير السينمائي",
                        desc = "لتصوير المشاهد وإضافتها في استوديو الفيديو والذكاء الاصطناعي",
                        accentColor = Color(0xFFA855F7)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExplanationDialog = false
                        launcher.launch(permissionsToRequest)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = DeepSlate),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("منح الصلاحيات 🚀", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExplanationDialog = false }) {
                    Text("لاحقاً", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun PermissionExplItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    accentColor: Color = GoldPrimary
) {
    Surface(
        color = Color(0xFF0E1524),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(desc, color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
    }
}
