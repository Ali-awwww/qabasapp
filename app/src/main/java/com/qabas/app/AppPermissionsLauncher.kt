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
        val theme = QabasTheme.current
        AlertDialog(
            onDismissRequest = { showExplanationDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(
                1.dp,
                theme.cardBorder,
                RoundedCornerShape(20.dp)
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.primary.copy(alpha = 0.12f))
                            .border(1.dp, theme.primary.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("صلاحيات استوديو مساعد المسلم", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("SPATIAL PERMISSION HUD", color = theme.primary, fontFamily = NotoSansFont, fontSize = 9.sp, letterSpacing = 0.5.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "للحصول على أفضل تجربة واستفادة كاملة من أدوات الاستوديو، يحتاج التطبيق إلى الصلاحيات التالية:",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
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
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("منح الصلاحيات 🚀", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExplanationDialog = false }) {
                    Text("لاحقاً", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 12.sp)
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
    val theme = QabasTheme.current
    Surface(
        color = theme.secondarySurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, theme.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(desc, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 10.5.sp, lineHeight = 15.sp)
            }
        }
    }
}
