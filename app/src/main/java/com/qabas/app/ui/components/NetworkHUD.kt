package com.qabas.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ConnectivityObserver
import com.qabas.app.UserPreferencesManager
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont
import kotlinx.coroutines.delay

@Composable
fun NetworkHUD() {
    val status by UserPreferencesManager.networkStatus.collectAsState()
    val isOffline = status != ConnectivityObserver.Status.Available
    var isDismissedManually by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        if (!isOffline) {
            isDismissedManually = false
        }
    }

    AnimatedVisibility(
        visible = isOffline && !isDismissedManually,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .widthIn(max = 420.dp)
                .shadow(12.dp, RoundedCornerShape(24.dp)),
            color = Color(0xFF1E1214).copy(alpha = 0.95f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Offline",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "وضع عدم الاتصال",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "// OFFLINE CACHE",
                            color = Color(0xFFEF4444).copy(alpha = 0.8f),
                            fontSize = 8.sp,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "التعديلات تُحفظ محلياً وتُزامن سحابياً فور العودة 📡",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontFamily = CairoFont
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { isDismissedManually = true },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إخفاء",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineHUD() {
    val status by UserPreferencesManager.networkStatus.collectAsState()
    var showOnlineMessage by remember { mutableStateOf(false) }
    var previousStatus by remember { mutableStateOf<ConnectivityObserver.Status?>(null) }

    LaunchedEffect(status) {
        if (previousStatus != null && previousStatus != ConnectivityObserver.Status.Available && status == ConnectivityObserver.Status.Available) {
            showOnlineMessage = true
            delay(3500)
            showOnlineMessage = false
        }
        previousStatus = status
    }

    AnimatedVisibility(
        visible = showOnlineMessage,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .shadow(10.dp, RoundedCornerShape(20.dp)),
            color = Color(0xFF064E3B).copy(alpha = 0.95f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Online",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "تم استعادة الاتصال السحابي بنجاح // SYNC ACTIVE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CairoFont
                )
            }
        }
    }
}
