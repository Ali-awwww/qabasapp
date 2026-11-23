package com.qabas.app.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.SupabaseServices
import com.qabas.app.UserPreferencesManager
import com.qabas.app.ui.theme.AppThemePreset
import com.qabas.app.ui.theme.CyberDarkGreen
import com.qabas.app.ui.theme.CyberDarkOrange
import com.qabas.app.ui.theme.CyberDarkSecondarySurface
import com.qabas.app.ui.theme.CyberDarkSecondaryLabel
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.ThemeManager

/**
 * حالات المزامنة السحابية
 */
enum class CloudSyncState {
    CONNECTED,   // متصل بسحابة Supabase ومستقر
    SYNCING,     // جاري المزامنة وحفظ البيانات
    OFFLINE      // غير متصل بالشبكة (التحول التلقائي للتخزين المحلي)
}

/**
 * مراقب حالة الاتصال بالشبكة كـ State في Compose
 */
@Composable
fun rememberIsNetworkAvailable(): State<Boolean> {
    val context = LocalContext.current
    val isAvailable = remember { mutableStateOf(checkInitialNetwork(context)) }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isAvailable.value = true
            }

            override fun onLost(network: Network) {
                isAvailable.value = false
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm?.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            isAvailable.value = true
        }

        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }

    return isAvailable
}

private fun checkInitialNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * مكون HUD فضائي يعكس حالة المزامنة والاتصال بسحابة Supabase
 */
@Composable
fun CloudSyncIndicator(
    modifier: Modifier = Modifier,
    isSyncing: Boolean = false,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val isNetworkOnline by rememberIsNetworkAvailable()
    val isSupabaseConfigured = remember { SupabaseServices.isSupabaseAvailable }
    // آخر مزامنة إعدادات ناجحة حقيقية (رفع أو تنزيل) — المصدر الوحيد الذي
    // يسمح بالحديث عن «محدَّثة»: بلا هذا الطابع لا يُقاس إلا الاتصال.
    val lastSyncTime by UserPreferencesManager.lastSyncTime.collectAsState()
    val isCyberTheme = ThemeManager.activePreset == AppThemePreset.CYBER_HUD_BLUE

    val syncState = remember(isNetworkOnline, isSupabaseConfigured, isSyncing) {
        when {
            isSyncing -> CloudSyncState.SYNCING
            isNetworkOnline && isSupabaseConfigured -> CloudSyncState.CONNECTED
            else -> CloudSyncState.OFFLINE
        }
    }

    // حركة النبض الضوئي أثناء المزامنة
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val accentColor by animateColorAsState(
        targetValue = when (syncState) {
            CloudSyncState.CONNECTED -> if (isCyberTheme) CyberDarkGreen else Color(0xFF10B981) // زمردي مشع
            CloudSyncState.SYNCING -> if (isCyberTheme) CyberDarkOrange else Color(0xFFFFA000)   // كهرماني ذهبي
            CloudSyncState.OFFLINE -> if (isCyberTheme) CyberDarkSecondaryLabel else Color(0xFF94A3B8)   // رمادي هادئ
        },
        label = "accentColor"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when (syncState) {
            CloudSyncState.CONNECTED -> if (isCyberTheme) CyberDarkGreen.copy(alpha = 0.18f) else Color(0xFF064E3B).copy(alpha = 0.35f)
            CloudSyncState.SYNCING -> if (isCyberTheme) CyberDarkOrange.copy(alpha = 0.18f) else Color(0xFF78350F).copy(alpha = 0.45f)
            CloudSyncState.OFFLINE -> if (isCyberTheme) CyberDarkSecondarySurface.copy(alpha = 0.7f) else Color(0xFF1E293B).copy(alpha = 0.5f)
        },
        label = "bgColor"
    )

    val statusText = when (syncState) {
        CloudSyncState.CONNECTED -> "متصل سحابياً"
        CloudSyncState.SYNCING -> "جاري المزامنة..."
        CloudSyncState.OFFLINE -> "تخزين محلي آمن"
    }

    Surface(
        color = backgroundColor,
        shape = CutCornerShape(6.dp),
        border = BorderStroke(
            0.8.dp,
            if (syncState == CloudSyncState.SYNCING) accentColor.copy(alpha = pulseAlpha) else accentColor.copy(alpha = 0.45f)
        ),
        modifier = modifier.clickable {
            val msg = when (syncState) {
                // «متصل» فقط — ما يُقاس هنا اتصال الشبكة + إعداد Supabase، لا
                // حداثة البيانات. التحديث الحقيقي يُذكر بتاريخه المقاس، وبنطاقه
                // (الإعدادات) لا «كل شيء محدَّث». بلا أي مزامنة ناجحة بعد، يُقال ذلك.
                CloudSyncState.CONNECTED -> if (lastSyncTime > 0L) {
                    val formatted = java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm", java.util.Locale.getDefault()
                    ).format(java.util.Date(lastSyncTime))
                    "متصل بسحابة Supabase — آخر مزامنة إعدادات ناجحة: $formatted"
                } else {
                    "متصل بسحابة Supabase — لم تتم مزامنة الإعدادات بعد"
                }
                // المتصلون يمررون حالات تحميل (تحديث لوحة شرف/دوري) لا رفعاً —
                // فلا يُدَّعى «رفع التغييرات».
                CloudSyncState.SYNCING -> "جارٍ تحديث البيانات مع سحابة Supabase..."
                CloudSyncState.OFFLINE -> "غير متصل بالشبكة: يتم الحفظ محلياً تلقائياً وستتم المزامنة فور عودة الاتصال"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // مؤشر نقطي نابض
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = if (syncState == CloudSyncState.SYNCING) accentColor.copy(alpha = pulseAlpha) else accentColor,
                        shape = CircleShape
                    )
            )

            // أيقونة الحالة
            when (syncState) {
                CloudSyncState.SYNCING -> {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "جاري المزامنة",
                        tint = accentColor,
                        modifier = Modifier
                            .size(13.dp)
                            .rotate(spinAngle)
                    )
                }
                CloudSyncState.CONNECTED -> {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "متصل",
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
                CloudSyncState.OFFLINE -> {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "حفظ محلي",
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            if (!compact) {
                Text(
                    text = statusText,
                    color = accentColor,
                    fontFamily = NotoSansFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(if (syncState == CloudSyncState.SYNCING) pulseAlpha else 1f)
                )
            }
        }
    }
}
