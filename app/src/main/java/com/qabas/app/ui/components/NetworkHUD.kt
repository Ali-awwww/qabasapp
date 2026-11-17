package com.qabas.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ConnectivityObserver
import com.qabas.app.UserPreferencesManager
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.delay

@Composable
fun NetworkHUD() {
    val status by UserPreferencesManager.networkStatus.collectAsState()
    val isOffline = status != ConnectivityObserver.Status.Available
    var isDismissedManually by remember { mutableStateOf(false) }
    val theme = QabasTheme.current

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
                .widthIn(max = 420.dp),
            color = theme.cardSurface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = if (theme.isDark) 2.dp else 6.dp,
            border = BorderStroke(0.8.dp, theme.destructive.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(theme.destructive.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "غير متصل بالإنترنت",
                        tint = theme.destructive,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "وضع عدم الاتصال",
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont
                    )
                    Text(
                        text = "يتم الحفظ محلياً وستتم المزامنة تلقائياً عند عودة الاتصال.",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = NotoSansFont
                    )
                }

                IconButton(
                    onClick = { isDismissedManually = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إخفاء",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(16.dp)
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
    val theme = QabasTheme.current

    LaunchedEffect(status) {
        if (previousStatus != null && previousStatus != ConnectivityObserver.Status.Available && status == ConnectivityObserver.Status.Available) {
            showOnlineMessage = true
            delay(3200)
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            color = theme.cardSurface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = if (theme.isDark) 2.dp else 5.dp,
            border = BorderStroke(0.8.dp, theme.success.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(theme.success.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "تم الاتصال",
                        tint = theme.success,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "تمت استعادة الاتصال والمزامنة بنجاح",
                    color = theme.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CairoFont
                )
            }
        }
    }
}
