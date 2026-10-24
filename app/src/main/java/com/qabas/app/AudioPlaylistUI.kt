package com.qabas.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.TestTags
import androidx.compose.ui.platform.testTag

/**
 * شريط التحكم الصوتي المتقدم — يعمل في الخلفية مع قائمة التشغيل والخصائص الاحترافية
 */
@Composable
fun ProAudioPlayerBar(
    onApplyToProject: (audioUrl: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val currentPositionMs by AudioPlaybackManager.currentPositionMs.collectAsState()
    val totalDurationMs by AudioPlaybackManager.totalDurationMs.collectAsState()
    val playbackSpeed by AudioPlaybackManager.playbackSpeed.collectAsState()
    val repeatMode by AudioPlaybackManager.repeatMode.collectAsState()
    val isShuffle by AudioPlaybackManager.isShuffle.collectAsState()
    val playlist by AudioPlaybackManager.playlist.collectAsState()
    val sleepTimerSeconds by AudioPlaybackManager.sleepTimerRemainingSeconds.collectAsState()

    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val track = currentTrack ?: return@AnimatedVisibility

        Surface(
            modifier = Modifier.testTag(TestTags.AUDIO_PLAYER_BAR),
            color = Color(0xFF0F172A).copy(alpha = 0.98f),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // 1. Header: Track info + Apply button + Sleep Timer badge + Playlist icon + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.15f))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPlaying) {
                                // Animated HUD Visualizer Icon
                                val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
                                Row(
                                    modifier = Modifier.padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    listOf(0.4f, 0.9f, 0.6f, 0.8f).forEachIndexed { index, heightFactor ->
                                        val height by infiniteTransition.animateFloat(
                                            initialValue = 4f,
                                            targetValue = 16f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(durationMillis = 400 + (index * 150), easing = FastOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "bar_$index"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(height.dp)
                                                .background(GoldPrimary, RoundedCornerShape(1.dp))
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = TextPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.artist.ifBlank { "قبس" },
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp
                                )
                                if (sleepTimerSeconds > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val mins = sleepTimerSeconds / 60
                                    val secs = sleepTimerSeconds % 60
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "⏱️ ${String.format(java.util.Locale.US, "%02d:%02d", mins, secs)}",
                                            color = GoldPrimary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // YouTube Link Button
                        IconButton(
                            onClick = {
                                val ytUrl = if (!track.youtubeUrl.isNullOrBlank()) {
                                    track.youtubeUrl!!
                                } else {
                                    "https://www.youtube.com/results?search_query=${Uri.encode("${track.title} ${track.artist}")}"
                                }
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "تعذر فتح يوتيوب", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.OndemandVideo,
                                contentDescription = "مشاهدة / استماع عبر يوتيوب 📺",
                                tint = Color(0xFFFF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Sleep Timer trigger button
                        IconButton(
                            onClick = { showSleepTimerDialog = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "مؤقت النوم",
                                tint = if (sleepTimerSeconds > 0) GoldPrimary else TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Playlist Sheet button
                        IconButton(
                            onClick = { showPlaylistSheet = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (playlist.size > 1) {
                                        Badge(containerColor = GoldPrimary, contentColor = DeepSlate) {
                                            Text("${playlist.size}", fontSize = 9.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = "قائمة التشغيل",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Apply to Project Button
                        Button(
                            onClick = { onApplyToProject(track.audioUrl, track.title) },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("اعتماد", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }

                        // Close button
                        IconButton(
                            onClick = { AudioPlaybackManager.clearPlaylist() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق المشغل", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Luxury Waveform Timeline Bar with Live Progress
                LuxuryAudioTimelineBar(
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    isPlaying = isPlaying,
                    onSeek = { targetMs ->
                        AudioPlaybackManager.seekTo(context, targetMs)
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Accurate Live Countdown & Elapsed Timer HUD
                AccurateAudioCountdownHud(
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    isPlaying = isPlaying
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Dynamic fluid transition for Play/Pause visual feedback (#play-pause-btn)
                val playPauseBtnBg by animateColorAsState(
                    targetValue = if (isPlaying) Color(0xFF10B981) else GoldPrimary,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                    label = "play_pause_bg"
                )
                val playPauseIconTint by animateColorAsState(
                    targetValue = if (isPlaying) Color.White else DeepSlate,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                    label = "play_pause_icon_tint"
                )
                val playPauseScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1.06f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "play_pause_scale"
                )

                fun togglePlayState() {
                    AudioPlaybackManager.togglePlayState(context)
                }

                // 4. Pro Playback Controls & Speed/Mode Selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Live Status Chip
                    Surface(
                        color = if (isPlaying) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isPlaying) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) Color(0xFF10B981) else TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPlaying) "تشغيل حي ⚡" else "متوقف مؤقتاً",
                                color = if (isPlaying) Color(0xFF10B981) else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    // Pro Controls: Shuffle | Prev | -10s | Play/Pause | +10s | Next | Repeat
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Shuffle
                        IconButton(
                            onClick = { AudioPlaybackManager.toggleShuffle() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "خلط عشوائي",
                                tint = if (isShuffle) GoldPrimary else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Prev Track
                        IconButton(
                            onClick = { AudioPlaybackManager.previous(context) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "المقطع السابق", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        }

                        // -10s
                        IconButton(
                            onClick = {
                                val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                                AudioPlaybackManager.seekTo(context, target)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "تراجع 10 ثوان", tint = GoldPrimary.copy(alpha = 0.8f), modifier = Modifier.size(17.dp))
                        }

                        // Play/Pause button (#play-pause-btn)
                        IconButton(
                            onClick = { togglePlayState() },
                            modifier = Modifier
                                .size(38.dp)
                                .scale(playPauseScale)
                                .clip(CircleShape)
                                .background(playPauseBtnBg)
                                .testTag("play-pause-btn")
                                .testTag(TestTags.PLAY_PAUSE_BUTTON)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                tint = playPauseIconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // +10s
                        IconButton(
                            onClick = {
                                val target = (currentPositionMs + 10000L).coerceAtMost(totalDurationMs)
                                AudioPlaybackManager.seekTo(context, target)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "تقديم 10 ثوان", tint = GoldPrimary.copy(alpha = 0.8f), modifier = Modifier.size(17.dp))
                        }

                        // Next Track
                        IconButton(
                            onClick = { AudioPlaybackManager.next(context) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "المقطع التالي", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        }

                        // Repeat Mode
                        IconButton(
                            onClick = { AudioPlaybackManager.cycleRepeatMode() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    AudioRepeatMode.ONE -> Icons.Default.RepeatOne
                                    AudioRepeatMode.ALL -> Icons.Default.Repeat
                                    AudioRepeatMode.OFF -> Icons.Default.Repeat
                                },
                                contentDescription = "وضع التكرار",
                                tint = if (repeatMode != AudioRepeatMode.OFF) GoldPrimary else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Speed chip
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.clickable {
                            val nextSpeed = when (playbackSpeed) {
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 1.0f
                            }
                            AudioPlaybackManager.setPlaybackSpeed(nextSpeed)
                        }
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = GoldPrimary,
                            fontFamily = NotoSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    // Playlist Bottom Sheet Dialog
    if (showPlaylistSheet) {
        AudioPlaylistBottomSheet(
            onDismiss = { showPlaylistSheet = false }
        )
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentSeconds = sleepTimerSeconds,
            onDismiss = { showSleepTimerDialog = false },
            onSelectMinutes = { mins ->
                AudioPlaybackManager.setSleepTimer(mins)
                showSleepTimerDialog = false
            }
        )
    }
}

/**
 * نافذة قائمة التشغيل الاحترافية (Playlist Sheet)
 */
@Composable
fun AudioPlaylistBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playlist by AudioPlaybackManager.playlist.collectAsState()
    val currentTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val isShuffle by AudioPlaybackManager.isShuffle.collectAsState()
    val repeatMode by AudioPlaybackManager.repeatMode.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "قائمة التشغيل الحالية",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            "${playlist.size} مقاطع في الطابور",
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { AudioPlaybackManager.toggleShuffle() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "خلط",
                            tint = if (isShuffle) GoldPrimary else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { AudioPlaybackManager.cycleRepeatMode() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                AudioRepeatMode.ONE -> Icons.Default.RepeatOne
                                AudioRepeatMode.ALL -> Icons.Default.Repeat
                                AudioRepeatMode.OFF -> Icons.Default.Repeat
                            },
                            contentDescription = "تكرار",
                            tint = if (repeatMode != AudioRepeatMode.OFF) GoldPrimary else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        text = {
            if (playlist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "قائمة التشغيل فارغة حالياً.\nاختر أي تلاوة أو مؤثر صوتي للبدء.",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(playlist) { index, item ->
                        val isCurrent = currentTrack?.id == item.id
                        Surface(
                            color = if (isCurrent) GoldPrimary.copy(alpha = 0.12f) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isCurrent) GoldPrimary.copy(alpha = 0.6f) else Color(0xFF2A344A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AudioPlaybackManager.playTrack(context, item)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (isCurrent) GoldPrimary else Color(0xFF0F172A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrent && isPlaying) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(14.dp))
                                        } else {
                                            Text(
                                                "${index + 1}",
                                                color = if (isCurrent) DeepSlate else TextSecondary,
                                                fontFamily = NotoSansFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = if (isCurrent) GoldPrimary else TextPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.artist} • ${item.category}",
                                            color = TextSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { AudioPlaybackManager.removeFromPlaylist(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444).copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (playlist.isNotEmpty()) {
                TextButton(
                    onClick = { AudioPlaybackManager.clearPlaylist() }
                ) {
                    Text("مسح القائمة", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("إغلاق", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    )
}

/**
 * نافذة مؤقت النوم الذكي (Smart Sleep Timer Dialog)
 */
@Composable
fun SleepTimerDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit
) {
    val presets = listOf(
        Pair("إيقاف المؤقت", 0),
        Pair("15 دقيقة", 15),
        Pair("30 دقيقة", 30),
        Pair("45 دقيقة", 45),
        Pair("60 دقيقة (ساعة)", 60)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = GoldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("مؤقت النوم الذكي ⏱️", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "يقوم بإيقاف تشغيل الصوت والتلاوة في الخلفية تلقائياً عند انتهاء الوقت المحدد لمساعدتك على الاسترخاء والنوم.",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                presets.forEach { (label, minutes) ->
                    val isSelected = if (minutes == 0) currentSeconds == 0 else (currentSeconds in (minutes * 60 - 59)..(minutes * 60))
                    Surface(
                        color = if (isSelected) GoldPrimary.copy(alpha = 0.15f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF2A344A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMinutes(minutes) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) GoldPrimary else TextPrimary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = TextSecondary, fontFamily = CairoFont)
            }
        }
    )
}
