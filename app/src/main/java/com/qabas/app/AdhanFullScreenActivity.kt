package com.qabas.app

import android.app.NotificationManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val selection = androidx.compose.runtime.remember { AdhanBackgroundPreferences.get(context) }
    var backgroundReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        backgroundReady = true
    }
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (backgroundReady) 1f else 0f,
        animationSpec = tween(850),
        label = "adhan_background_fade"
    )
    val pulseTransition = rememberInfiniteTransition(label = "adhan_icon_pulse")
    val iconScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "adhan_icon_scale"
    )
    val background = when (selection.value) {
        "black_gold" -> Brush.verticalGradient(listOf(Color(0xFF090909), Color(0xFF30250B), Color.Black))
        "green_night" -> Brush.verticalGradient(listOf(Color(0xFF041B17), Color(0xFF0B493A), Color(0xFF03100D)))
        "sky_night" -> Brush.verticalGradient(listOf(Color(0xFF071D36), Color(0xFF126080), Color(0xFF06101E)))
        else -> Brush.verticalGradient(listOf(Color(0xFF07162B), Color(0xFF0B2940), Color(0xFF06101E)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
        Box(Modifier.fillMaxSize().background(background).alpha(backgroundAlpha))
        if (selection.type == AdhanBackgroundPreferences.TYPE_IMAGE) {
            val bitmap = androidx.compose.runtime.remember(selection.value) {
                BitmapFactory.decodeFile(selection.value)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .alpha(backgroundAlpha)
                )
            }
        } else if (selection.type == AdhanBackgroundPreferences.TYPE_VIDEO) {
            AndroidView(
                factory = { viewContext ->
                    VideoView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(-1, -1)
                        setVideoURI(Uri.fromFile(java.io.File(selection.value)))
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            player.setVolume(0f, 0f)
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().alpha(backgroundAlpha),
                update = { if (!it.isPlaying) it.start() }
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = selection.dim)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.60f))
                )
            )
        )
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(700)) + slideInVertically(
                animationSpec = tween(700),
                initialOffsetY = { it / 12 }
            )
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
                modifier = Modifier.height(54.dp).scale(iconScale)
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
}
