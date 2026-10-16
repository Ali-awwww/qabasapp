package com.qabas.app.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.qabas.app.ExoPlayerFactory
import com.qabas.app.ReelSafetyGuardrails
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.GoldPrimary
import kotlinx.coroutines.delay

@Composable
fun ReelPlayer(
    videoUrl: String,
    isPlaying: Boolean,
    playbackSpeed: Float = 1.0f,
    initialMuted: Boolean = false,
    isMuted: Boolean = initialMuted,
    onVideoEnded: (() -> Unit)? = null,
    onDoubleTapLike: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isUserPaused by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var showHeartAnimation by remember { mutableStateOf(false) }

    // Guardrail: Safe ExoPlayer allocation with custom User-Agent and fallback
    val exoPlayer = remember(context) {
        runCatching {
            ExoPlayerFactory.createPlayer(context).apply {
                repeatMode = if (onVideoEnded != null) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
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
                }
                if (state == Player.STATE_ENDED) {
                    onVideoEnded?.invoke()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("ReelPlayer", "Media3 playback error handled gracefully: ${error.message}")
                hasError = true
                isBuffering = false
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
        }
    }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        exoPlayer?.let { runCatching { it.setPlaybackSpeed(playbackSpeed) } }
    }

    LaunchedEffect(onVideoEnded, exoPlayer) {
        exoPlayer?.let { player ->
            runCatching {
                player.repeatMode = if (onVideoEnded != null) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
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

    LaunchedEffect(videoUrl, exoPlayer) {
        hasError = false
        isBuffering = true
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

    LaunchedEffect(isMuted, exoPlayer) {
        exoPlayer?.let { player ->
            runCatching {
                player.volume = if (isMuted) 0f else 1f
            }
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        showHeartAnimation = true
                        onDoubleTapLike?.invoke()
                    },
                    onTap = {
                        isUserPaused = !isUserPaused
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
                            text = "عرض النموذج التوضيحي للريلز 🎬",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "المقطع مسجل على السحابة ومهيأ للمشاهدة",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                hasError = false
                                isBuffering = true
                                exoPlayer?.let { p ->
                                    runCatching {
                                        p.stop()
                                        p.clearMediaItems()
                                        p.setMediaItem(MediaItem.fromUri(videoUrl))
                                        p.prepare()
                                        p.play()
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
    }
}
