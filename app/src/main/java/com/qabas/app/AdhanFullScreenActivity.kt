package com.qabas.app

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * شاشة الأذان التي يفتحها Full-Screen Intent فوق شاشة القفل.
 * لا تُستخدم للعداد المباشر؛ مسار AzanCountdownService مستقل عنها.
 */
class AdhanFullScreenActivity : ComponentActivity() {
    private var prayerName by mutableStateOf("الصلاة")
    private var prayerNotificationId by mutableStateOf(0)
    private var stopped by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        readIntent(intent)
        render()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        setIntent(intent)
        readIntent(intent)
        render()
    }

    private fun configureLockScreenWindow() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
    }

    private fun readIntent(intent: android.content.Intent) {
        prayerName = intent.getStringExtra("prayer_name") ?: "الصلاة"
        prayerNotificationId = intent.getIntExtra("prayer_notification_id", 0)
    }

    private fun render() {
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    AdhanScreen(
                        prayerName = prayerName,
                        stopped = stopped,
                        onStop = ::stopAndClose
                    )
                }
            }
        }
    }

    private fun stopAndClose() {
        if (stopped) return
        stopped = true
        AzanPlaybackService.stopAzan(this)
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (prayerNotificationId != 0) {
            notificationManager?.cancel(prayerNotificationId)
        }
        notificationManager?.cancel(AzanPlaybackService.NOTIFICATION_ID)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        finishAndRemoveTask()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // الخروج من شاشة الأذان يعني إيقافه، مثل زر الإيقاف والسحب للأعلى.
        stopAndClose()
    }
}

@androidx.compose.runtime.Composable
private fun AdhanScreen(
    prayerName: String,
    stopped: Boolean,
    onStop: () -> Unit
) {
    var dragDistance = 0f
    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF07162B), Color(0xFF0B2940), Color(0xFF06101E))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount -> dragDistance += dragAmount },
                    onDragEnd = {
                        if (dragDistance < -120f) onStop()
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f }
                )
            }
            .padding(horizontal = 28.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = Color(0xFFFFD166),
                modifier = Modifier.height(54.dp)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = "قبس",
                color = Color(0xFFFFD166),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(30.dp))
            Text(
                text = if (stopped) "تم إيقاف الأذان" else "حان الآن وقت $prayerName",
                color = Color.White,
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (stopped) "يمكنك إغلاق هذه الشاشة" else "يُرفع الأذان الآن — تقبل الله صلاتكم",
                color = Color(0xFFD5E7F2),
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onStop,
                enabled = !stopped,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD4AF37),
                    contentColor = Color(0xFF101820),
                    disabledContainerColor = Color(0xFF53616B),
                    disabledContentColor = Color.White
                )
            ) {
                Text(
                    text = if (stopped) "تم الإيقاف" else "إيقاف الأذان",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "اسحب للأعلى لإيقاف الأذان",
                tint = Color(0xFFAED8E8),
                modifier = Modifier.height(36.dp)
            )
            Text(
                text = "اسحب للأعلى لإيقاف الأذان",
                color = Color(0xFFAED8E8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
