@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

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
import androidx.media3.ui.PlayerView
import com.qabas.app.ExoPlayerFactory
import com.qabas.app.ReelSafetyGuardrails
import com.qabas.app.VideoCacheService
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.ThemeDestructive
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
    aspectRatio: String = "9:16",
    playbackSpeed: Float = 1.0f,
    volume: Float = 1f,
    brightness: Float = 0.5f,
    onBrightnessChange: (Float) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onVideoEnded: (() -> Unit)? = null,
    onDoubleTapLike: (() -> Unit)? = null,
    onSingleTap: (() -> Unit)? = null,
    onActualPlayingChanged: ((Boolean) -> Unit)? = null,
    // ق81: استئناف المشاهدة — موضع البدء المحفوظ من جلسة سابقة
    // لهذا المقطع (0 = من البداية)، واستدعاء يُبلَّغ به الموضع
    // دورياً وعند التخلص ليحفظه الوالد عبر دورات حياة الصفحة.
    startPositionMs: Long = 0L,
    onPositionChanged: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val configuredDefaultSpeed = remember(context) { com.qabas.app.UnifiedVideoPlayerSettings.speed(context) }
    val effectivePlaybackSpeed = if (playbackSpeed == 1.0f) configuredDefaultSpeed else playbackSpeed
    var isUserPaused by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var showHeartAnimation by remember { mutableStateOf(false) }
    var isActuallyPlaying by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var videoDurationMs by remember { mutableLongStateOf(C.TIME_UNSET) }
    // ق81: آخر موضع تشغيل معروف في نسخة المشغّل هذه — يُستأنف
    // منه عند إعادة التحضير (خطأ/إعادة محاولة)، ويُبلَّغ للوالد.
    var lastPositionMs by remember { mutableLongStateOf(0L) }

    // Vertical gesture HUD (left half = brightness, right half = volume)
    var gestureKind by remember { mutableStateOf(GestureKind.NONE) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var showGestureHud by remember { mutableStateOf(false) }

    // آخر نسخة من الاستدعاءات — تُقرأ وقت الحدث لا وقت التكوين
    val currentOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val currentOnDoubleTapLike by rememberUpdatedState(onDoubleTapLike)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnActualPlaying by rememberUpdatedState(onActualPlayingChanged)
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)

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
            // ق81: retainPlayer() في remember يعمل حتى عند فشل إنشاء
            // المشغّل — بلا تحرير هنا يتسرب العدّاد +1 لكل فشل
            // فيمنع إغلاق الكاش المؤجل (onLowMemory) إلى الأبد.
            return@DisposableEffect onDispose { VideoCacheService.releasePlayer() }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    hasError = false
                    val d = exoPlayer.duration
                    if (d != C.TIME_UNSET) videoDurationMs = d
                    // ق81: شريط التقدم يصحح فور الجاهزية (لا بعد 500ms)
                    // حتى لا يومض من الصفر عند الاستئناف من موضع محفوظ.
                    if (d != C.TIME_UNSET && d > 0) {
                        progressFraction = (exoPlayer.currentPosition.toFloat() / d).coerceIn(0f, 1f)
                    }
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
                android.util.Log.w("ReelPlayer", "Media3 playback error handled: ${error.message}")
                hasError = true
                isBuffering = false
                isActuallyPlaying = false
                currentOnActualPlaying?.invoke(false)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            runCatching {
                // ق81: حفظ موضع الاستئناف قبل الإيقاف — الصفحة التي
                // تغادر نطاق الـ Pager تُحرَّر فيُفقد موضعها بلا هذا.
                // موضع قريب من النهاية (ثانية) يُحفظ صفراً ليعيد
                // المقطع من بدايته لا من لحظة انتهائه.
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                val save = if (dur != C.TIME_UNSET && dur > 0 && pos >= dur - 1000L) 0L else pos
                currentOnPositionChanged?.invoke(save)
                exoPlayer.removeListener(listener)
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            }
            // حرر مرجع الكاش بعد تحرير المشغّل فعلياً
            VideoCacheService.releasePlayer()
        }
    }

    LaunchedEffect(effectivePlaybackSpeed, exoPlayer) {
        exoPlayer?.let { runCatching { it.setPlaybackSpeed(effectivePlaybackSpeed) } }
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
        // ق81: startPositionMs ليس مفتاحاً عمداً — تغيّره أثناء حياة
        // المشغّل (إعادة تركيب من الوالد) يجب ألا يعيد تحضير المقطع
        // من جديد؛ يُقرأ مرة واحدة عند التحضير الأول فقط، وseekTo
        // بعد prepare مسموح في ExoPlayer (يُطبَّق عند الجاهزية).
        // مقطع جديد: صفّر حالة الإيقاف اليدوي — لا يرث المستخدم إيقاف المقطع السابق
        isUserPaused = false
        hasError = false
        isBuffering = true
        progressFraction = 0f
        videoDurationMs = C.TIME_UNSET
        lastPositionMs = startPositionMs
        if (exoPlayer == null || !ReelSafetyGuardrails.isValidSecureVideoUrl(videoUrl)) {
            hasError = true
            isBuffering = false
            return@LaunchedEffect
        }
        runCatching {
            val mediaItem = MediaItem.fromUri(videoUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (startPositionMs > 0L) exoPlayer.seekTo(startPositionMs)
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
                    if (hasError) {
                        hasError = false
                        isBuffering = true
                        player.stop()
                        player.clearMediaItems()
                        player.setMediaItem(MediaItem.fromUri(videoUrl))
                        player.prepare()
                        // ق81: إعادة التحضير بعد خطأ تستأنف من آخر
                        // موضع معروف لا من البداية.
                        if (lastPositionMs > 0L) player.seekTo(lastPositionMs)
                    }
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

    // Guardrail: Buffer Timeout Guard (max 15s before graceful fallback, only when actively playing)
    LaunchedEffect(isBuffering, isPlaying, isUserPaused) {
        if (isBuffering && isPlaying && !isUserPaused) {
            delay(15_000)
            if (isBuffering && isPlaying && !isUserPaused) {
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
            // ق81: تتبّع موضع الاستئناف أثناء التشغيل (كل 500ms) —
            // بلا هذا لا يُحفظ شيء إن قُتلت الصفحة قبل التخلص النظيف.
            lastPositionMs = player.currentPosition
            currentOnPositionChanged?.invoke(player.currentPosition)
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
        // The selected ratio defines the visible video canvas. Keep the entire source
        // frame visible inside it; ZOOM crops subtitles and text near the frame edges.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val targetAspectRatio = when (aspectRatio.trim()) {
                "9:16" -> 9f / 16f
                "1:1" -> 1f
                "16:9" -> 16f / 9f
                else -> 9f / 16f
            }

            AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            com.qabas.app.UnifiedVideoPlayerSettings.configure(this, ctx)
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                    // AndroidView is reused by Compose; always apply the latest shared player settings.
                    com.qabas.app.UnifiedVideoPlayerSettings.configure(view, view.context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(targetAspectRatio)
            )
        }

        // Loading / Error Poster inside cinematic video viewport
        if (isBuffering || hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF141418),
                                Color(0xFF000000),
                                Color(0xFF101014)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        color = theme.primary,
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 3.dp
                    )
                } else if (hasError) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.10f),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "تعذر تحميل المقطع حالياً",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تحقق من اتصالك بالإنترنت ثم أعد المحاولة",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                hasError = false
                                isBuffering = true
                                isUserPaused = false
                                exoPlayer?.let { p ->
                                    runCatching {
                                        p.stop()
                                        p.clearMediaItems()
                                        p.setMediaItem(MediaItem.fromUri(videoUrl))
                                        p.prepare()
                                        // ق81: إعادة المحاولة اليدوية تستأنف
                                        // من آخر موضع معروف لا من البداية.
                                        if (lastPositionMs > 0L) p.seekTo(lastPositionMs)
                                        if (isPlaying) {
                                            p.play()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إعادة المحاولة", fontSize = 14.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
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
                tint = ThemeDestructive,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(110.dp)
                    .scale(heartScale)
            )
        }

        // Cyberpunk HUD overlay (brightness / volume)
        AnimatedVisibility(
            visible = showGestureHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = theme.cardSurface.copy(alpha = 0.94f),
                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.7f)),
                modifier = Modifier.size(width = 92.dp, height = 152.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = if (gestureKind == GestureKind.BRIGHTNESS) Icons.Default.BrightnessHigh else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = theme.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(gestureValue.coerceIn(0f, 1f))
                                .clip(CircleShape)
                                .background(theme.primary)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(gestureValue.coerceIn(0f, 1f) * 100).toInt()}%",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
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
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(theme.primary)
                )
            }
        }
    }
}

private enum class GestureKind { NONE, BRIGHTNESS, VOLUME }
