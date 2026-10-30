package com.qabas.app.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.qabas.app.ExoPlayerFactory
import com.qabas.app.ReelSafetyGuardrails
import com.qabas.app.VideoCacheService
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * مشغّل مقطع ريل واحد — مبني على Media3/ExoPlayer عبر المصنع المركزي مع:
 * - كاش على القرص (لا تُعاد تنزيلات المشاهدات المتكررة)
 * - طلب تركيز الصوت + إيقاف تلقائي عند فصل السماعة
 * - بوابة تشغيل موحّدة واحدة: isPlaying (الصفحة مرئية + الشاشة نشطة + الشبكة مسموحة)،
 *   وزر «إعادة المحاولة» يعيد التحضير فقط ولا يتجاوز البوابة أبداً
 * - إيماءات عمودية (يسار: سطوع، يمين: صوت) مع HUD هندسي
 * - شريط تقدم رفيع أسفل الشاشة
 * - onActualPlayingChanged: يُخبر الشاشة بحالة التشغيل الفعلية (لا التقديرية)
 *   لقياس مدة المشاهدة الحقيقية
 */
@Composable
fun ReelPlayer(
    videoUrl: String,
    isPlaying: Boolean,
    playbackSpeed: Float = 1.0f,
    volume: Float = 1f,
    brightness: Float = 0.5f,
    onBrightnessChange: (Float) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onVideoEnded: (() -> Unit)? = null,
    onDoubleTapLike: (() -> Unit)? = null,
    onSingleTap: (() -> Unit)? = null,
    onActualPlayingChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isUserPaused by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var showHeartAnimation by remember { mutableStateOf(false) }
    var isActuallyPlaying by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var videoDurationMs by remember { mutableLongStateOf(C.TIME_UNSET) }

    // Vertical gesture HUD (left half = brightness, right half = volume)
    var gestureKind by remember { mutableStateOf(GestureKind.NONE) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var showGestureHud by remember { mutableStateOf(false) }

    // آخر نسخة من الاستدعاءات — تُقرأ وقت الحدث لا وقت التكوين
    val currentOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val currentOnDoubleTapLike by rememberUpdatedState(onDoubleTapLike)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnActualPlaying by rememberUpdatedState(onActualPlayingChanged)

    // Guardrail: Safe ExoPlayer allocation with custom User-Agent and fallback
    val exoPlayer = remember(context) {
        runCatching {
            VideoCacheService.retainPlayer()
            ExoPlayerFactory.createPlayer(context, useCache = true, useAudioFocus = true).apply {
                repeatMode = if (currentOnVideoEnded != null) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            }
        }.getOrNull()
    }

    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) {
            hasError = true
            isBuffering = false
            return@DisposableEffect onDispose {}
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    hasError = false
                    val d = exoPlayer.duration
                    if (d != C.TIME_UNSET) videoDurationMs = d
                }
                if (state == Player.STATE_ENDED) {
                    currentOnVideoEnded?.invoke()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isActuallyPlaying = playing
                currentOnActualPlaying?.invoke(playing)
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("ReelPlayer", "Media3 playback error handled gracefully: ${error.message}")
                hasError = true
                isBuffering = false
                isActuallyPlaying = false
                currentOnActualPlaying?.invoke(false)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            runCatching {
                exoPlayer.removeListener(listener)
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            }
            // حرر مرجع الكاش بعد تحرير المشغّل فعلياً
            VideoCacheService.releasePlayer()
        }
    }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        exoPlayer?.let { runCatching { it.setPlaybackSpeed(playbackSpeed) } }
    }

    // وضع التكرار يتبع وجود مستدعى «انتهى المقطع» (التمرير التلقائي) لا يتجمد على قيمة التكوين
    LaunchedEffect(onVideoEnded, exoPlayer) {
        exoPlayer?.let { player ->
            runCatching {
                player.repeatMode = if (onVideoEnded != null) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            }
        }
    }

    LaunchedEffect(videoUrl, exoPlayer) {
        // مقطع جديد: صفّر حالة الإيقاف اليدوي — لا يرث المستخدم إيقاف المقطع السابق
        isUserPaused = false
        hasError = false
        isBuffering = true
        progressFraction = 0f
        videoDurationMs = C.TIME_UNSET
        if (exoPlayer == null || !ReelSafetyGuardrails.isValidSecureVideoUrl(videoUrl)) {
            hasError = true
            isBuffering = false
            return@LaunchedEffect
        }
        runCatching {
            val mediaItem = MediaItem.fromUri(videoUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }.onFailure {
            hasError = true
            isBuffering = false
        }
    }

    // بوابة التشغيل الموحّدة: كل شيء يمر من هنا (الصفحة/الشاشة/الشبكة/إيقاف المستخدم)
    LaunchedEffect(isPlaying, isUserPaused, exoPlayer) {
        exoPlayer?.let { player ->
            runCatching {
                if (isPlaying && !isUserPaused) {
                    player.play()
                } else {
                    player.pause()
                }
            }
        }
    }

    LaunchedEffect(volume, exoPlayer) {
        exoPlayer?.let { player ->
            runCatching {
                player.volume = volume.coerceIn(0f, 1f)
            }
        }
    }

    // Guardrail: Buffer Timeout Guard (max 12s before graceful fallback)
    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            delay(12_000)
            if (isBuffering) {
                hasError = true
                isBuffering = false
            }
        }
    }

    // شريط التقدم: تحديث أثناء التشغيل الفعلي فقط
    LaunchedEffect(isActuallyPlaying, videoUrl, exoPlayer) {
        if (!isActuallyPlaying || exoPlayer == null) return@LaunchedEffect
        while (isActive) {
            val player = exoPlayer ?: break
            val d = player.duration
            if (d != C.TIME_UNSET && d > 0) {
                progressFraction = (player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
            }
            delay(500)
        }
    }

    // Heart pop animation scale
    val heartScale by animateFloatAsState(
        targetValue = if (showHeartAnimation) 1.4f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heart_pop"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // مُفتاح بعنوان المقطع: إيماءات جديدة نظيفة لكل ريل (لا تتسرب حالة لمقطع آخر)
            .pointerInput(videoUrl) {
                detectTapGestures(
                    onLongPress = { /* reserved so long-press never falls through to a tap */ },
                    onDoubleTap = {
                        showHeartAnimation = true
                        currentOnDoubleTapLike?.invoke()
                    },
                    onTap = {
                        // النقرة = إيقاف/تشغيل مؤقت دائماً (بارتي تيك توك) + استدعاء اختياري
                        // لإظهار الواجهة — لا تتعارض مع النقر المزدوج (مثل الإعجاب)
                        isUserPaused = !isUserPaused
                        currentOnSingleTap?.invoke()
                    }
                )
            }
            .pointerInput(videoUrl) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val isLeftHalf = offset.x < size.width / 2f
                        gestureKind = if (isLeftHalf) GestureKind.BRIGHTNESS else GestureKind.VOLUME
                        gestureValue = if (isLeftHalf) brightness else volume
                        showGestureHud = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.y / size.height.toFloat()
                        gestureValue = (gestureValue + delta).coerceIn(0f, 1f)
                        when (gestureKind) {
                            GestureKind.BRIGHTNESS -> onBrightnessChange(gestureValue)
                            GestureKind.VOLUME -> onVolumeChange(gestureValue)
                            GestureKind.NONE -> Unit
                        }
                    },
                    onDragEnd = { showGestureHud = false },
                    onDragCancel = {
                        showGestureHud = false
                        gestureKind = GestureKind.NONE
                    }
                )
            }
    ) {
        // Video View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Shimmer / Poster Fallback when buffering or error
        if (isBuffering || hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0F172A),
                                Color(0xFF020617),
                                Color(0xFF1E1B4B)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        color = GoldPrimary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                } else if (hasError) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "تعذر تحميل المقطع حالياً",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تحقق من اتصالك بالإنترنت ثم أعد المحاولة",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                // إعادة المحضير فقط — بوابة التشغيل الموحّدة تتكفل بالتشغيل
                                hasError = false
                                isBuffering = true
                                exoPlayer?.let { p ->
                                    runCatching {
                                        p.stop()
                                        p.clearMediaItems()
                                        p.setMediaItem(MediaItem.fromUri(videoUrl))
                                        p.prepare()
                                        p.playWhenReady = true
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = GoldPrimary
                            ),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إعادة المحاولة 🔄", fontSize = 12.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Animated Pause Indicator overlay in center
        AnimatedVisibility(
            visible = isUserPaused,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "تشغيل",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        // Double-Tap Burst Heart Animation
        if (showHeartAnimation) {
            LaunchedEffect(Unit) {
                delay(650)
                showHeartAnimation = false
            }
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(110.dp)
                    .scale(heartScale)
            )
        }

        // Geometric gesture HUD (brightness / volume)
        AnimatedVisibility(
            visible = showGestureHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CutCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.size(width = 92.dp, height = 150.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = if (gestureKind == GestureKind.BRIGHTNESS) Icons.Default.BrightnessHigh else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(64.dp)
                            .clip(CutCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(gestureValue.coerceIn(0f, 1f))
                                .clip(CutCornerShape(5.dp))
                                .background(GoldPrimary)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${(gestureValue.coerceIn(0f, 1f) * 100).toInt()}%",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // شريط تقدم رفيع أسفل الشاشة (لا يُعرض إن كانت المدة غير معروفة)
        if (videoDurationMs != C.TIME_UNSET && !hasError) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(GoldPrimary)
                )
            }
        }
    }
}

private enum class GestureKind { NONE, BRIGHTNESS, VOLUME }
