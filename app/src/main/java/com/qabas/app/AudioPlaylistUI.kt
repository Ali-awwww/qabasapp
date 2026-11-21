package com.qabas.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.TestTags
import com.qabas.app.ui.theme.*
import java.util.Locale

/**
 * شريط التحكم الصوتي بنمط Apple Mini Player — يعمل في الخلفية مع قائمة التشغيل والخصائص المتقدمة.
 */
@Composable
fun ProAudioPlayerBar(
    onApplyToProject: (audioUrl: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
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

    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val track = currentTrack ?: return@AnimatedVisibility

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.AUDIO_PLAYER_BAR),
            color = theme.cardSurface.copy(alpha = 0.98f),
            border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.65f)),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            shadowElevation = if (theme.isDark) 4.dp else 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // 1. Header: Track info + Quick actions
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
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPlaying) {
                                val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
                                Row(
                                    modifier = Modifier.padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    listOf(0.4f, 0.9f, 0.6f, 0.8f).forEachIndexed { index, _ ->
                                        val height by infiniteTransition.animateFloat(
                                            initialValue = 4f,
                                            targetValue = 16f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(durationMillis = 420 + (index * 140), easing = FastOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "bar_$index"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(height.dp)
                                                .background(theme.primary, RoundedCornerShape(1.dp))
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.artist.ifBlank { "مساعد المسلم" },
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (sleepTimerSeconds > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val mins = sleepTimerSeconds / 60
                                    val secs = sleepTimerSeconds % 60
                                    Surface(
                                        color = theme.success.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%02d:%02d", mins, secs),
                                            color = theme.success,
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val ytUrl = if (!track.youtubeUrl.isNullOrBlank()) {
                                    track.youtubeUrl
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
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.OndemandVideo,
                                contentDescription = "مشاهدة عبر يوتيوب",
                                tint = theme.destructive,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = { showSleepTimerDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "مؤقت النوم",
                                tint = if (sleepTimerSeconds > 0) theme.primary else theme.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { showPlaylistSheet = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (playlist.size > 1) {
                                        Badge(containerColor = theme.primary, contentColor = Color.White) {
                                            Text("${playlist.size}", fontSize = 9.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = "قائمة التشغيل",
                                    tint = theme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { onApplyToProject(track.audioUrl, track.title) },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اعتماد", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = { AudioPlaybackManager.clearPlaylist() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "إغلاق المشغل",
                                tint = theme.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LuxuryAudioTimelineBar(
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    isPlaying = isPlaying,
                    onSeek = { targetMs -> AudioPlaybackManager.seekTo(context, targetMs) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                AccurateAudioCountdownHud(
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    isPlaying = isPlaying
                )

                Spacer(modifier = Modifier.height(6.dp))

                val playPauseBtnBg by animateColorAsState(
                    targetValue = if (isPlaying) theme.success else theme.primary,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    label = "play_pause_bg"
                )
                val playPauseScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1.05f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "play_pause_scale"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = if (isPlaying) theme.success.copy(alpha = 0.14f) else theme.secondarySurface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) theme.success else theme.textSecondary)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isPlaying) "يعمل الآن" else "متوقف",
                                color = if (isPlaying) theme.success else theme.textSecondary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { AudioPlaybackManager.toggleShuffle() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "خلط عشوائي",
                                tint = if (isShuffle) theme.primary else theme.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        IconButton(
                            onClick = { AudioPlaybackManager.previous(context) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "المقطع السابق", tint = theme.textPrimary, modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = {
                                val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                                AudioPlaybackManager.seekTo(context, target)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "تراجع 10 ثوان", tint = theme.primary, modifier = Modifier.size(19.dp))
                        }

                        IconButton(
                            onClick = { AudioPlaybackManager.togglePlayState(context) },
                            modifier = Modifier
                                .size(48.dp)
                                .scale(playPauseScale)
                                .clip(CircleShape)
                                .background(playPauseBtnBg)
                                .testTag("play-pause-btn")
                                .testTag(TestTags.PLAY_PAUSE_BUTTON)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val target = (currentPositionMs + 10000L).coerceAtMost(totalDurationMs)
                                AudioPlaybackManager.seekTo(context, target)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "تقديم 10 ثوان", tint = theme.primary, modifier = Modifier.size(19.dp))
                        }

                        IconButton(
                            onClick = { AudioPlaybackManager.next(context) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "المقطع التالي", tint = theme.textPrimary, modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = { AudioPlaybackManager.cycleRepeatMode() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    AudioRepeatMode.ONE -> Icons.Default.RepeatOne
                                    AudioRepeatMode.ALL -> Icons.Default.Repeat
                                    AudioRepeatMode.OFF -> Icons.Default.Repeat
                                },
                                contentDescription = "وضع التكرار",
                                tint = if (repeatMode != AudioRepeatMode.OFF) theme.primary else theme.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            val nextSpeed = when (playbackSpeed) {
                                0.5f -> 0.75f
                                0.75f -> 1.0f
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 1.75f
                                1.75f -> 2.0f
                                2.0f -> 0.5f
                                else -> 1.25f
                            }
                            AudioPlaybackManager.setPlaybackSpeed(nextSpeed)
                        }
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = theme.primary,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showPlaylistSheet) {
        AudioPlaylistBottomSheet(
            onDismiss = { showPlaylistSheet = false }
        )
    }

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
 * نافذة قائمة التشغيل المختصرة
 */
@Composable
fun AudioPlaylistBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val playlist by AudioPlaybackManager.playlist.collectAsState()
    val currentTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val isShuffle by AudioPlaybackManager.isShuffle.collectAsState()
    val repeatMode by AudioPlaybackManager.repeatMode.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "قائمة التشغيل الحالية",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            "${playlist.size} مقاطع في الطابور",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { AudioPlaybackManager.toggleShuffle() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "خلط",
                            tint = if (isShuffle) theme.primary else theme.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { AudioPlaybackManager.cycleRepeatMode() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                AudioRepeatMode.ONE -> Icons.Default.RepeatOne
                                AudioRepeatMode.ALL -> Icons.Default.Repeat
                                AudioRepeatMode.OFF -> Icons.Default.Repeat
                            },
                            contentDescription = "تكرار",
                            tint = if (repeatMode != AudioRepeatMode.OFF) theme.primary else theme.textSecondary,
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
                        "قائمة التشغيل فارغة حالياً.\nاختر أي تلاوة أو مقطع صوتي للبدء.",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(playlist) { index, item ->
                        val isCurrent = currentTrack?.id == item.id
                        Surface(
                            color = if (isCurrent) theme.primary.copy(alpha = 0.12f) else theme.secondarySurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                if (isCurrent) 1.dp else 0.5.dp,
                                if (isCurrent) theme.primary else theme.separator.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { AudioPlaybackManager.playTrack(context, item) }
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
                                            .background(if (isCurrent) theme.primary else theme.cardSurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrent && isPlaying) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        } else {
                                            Text(
                                                "${index + 1}",
                                                color = if (isCurrent) Color.White else theme.textSecondary,
                                                fontFamily = RobotoMonoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = if (isCurrent) theme.primary else theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.artist} • ${item.category}",
                                            color = theme.textSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { AudioPlaybackManager.removeFromPlaylist(item.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "حذف",
                                        tint = theme.destructive,
                                        modifier = Modifier.size(18.dp)
                                    )
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
                    onClick = { AudioPlaybackManager.clearPlaylist() },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("مسح القائمة", color = theme.destructive, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("إغلاق", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}

/**
 * نافذة مؤقت النوم (Sleep Timer Dialog)
 */
@Composable
fun SleepTimerDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit
) {
    val theme = QabasTheme.current
    val presets = listOf(
        Pair("إيقاف المؤقت", 0),
        Pair("15 دقيقة", 15),
        Pair("30 دقيقة", 30),
        Pair("45 دقيقة", 45),
        Pair("60 دقيقة (ساعة)", 60)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = theme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("مؤقت النوم الذكي", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "يوقف تشغيل الصوت والتلاوة في الخلفية تلقائياً عند انتهاء الوقت المحدد.",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                presets.forEach { (label, minutes) ->
                    val isSelected = if (minutes == 0) currentSeconds == 0 else (currentSeconds in (minutes * 60 - 59)..(minutes * 60))
                    Surface(
                        color = if (isSelected) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            if (isSelected) 1.dp else 0.5.dp,
                            if (isSelected) theme.primary else theme.separator.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onSelectMinutes(minutes) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) theme.primary else theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("إغلاق", color = theme.textSecondary, fontFamily = CairoFont)
            }
        }
    )
}
