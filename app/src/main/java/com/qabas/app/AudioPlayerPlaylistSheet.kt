package com.qabas.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.*
import java.util.Locale

/**
 * نافذة قائمة التشغيل (Playlist Bottom Sheet) بنمط Apple HIG في الوضعين الفاتح والداكن.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlaylistBottomSheet(
    onDismiss: () -> Unit,
    onOpenStudioWithTrack: ((PlayableAudioTrack) -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val currentTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val playlist by AudioPlaybackManager.playlist.collectAsState()
    val currentIndex by AudioPlaybackManager.currentIndex.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val currentPositionMs by AudioPlaybackManager.currentPositionMs.collectAsState()
    val totalDurationMs by AudioPlaybackManager.totalDurationMs.collectAsState()
    val playbackSpeed by AudioPlaybackManager.playbackSpeed.collectAsState()
    val repeatMode by AudioPlaybackManager.repeatMode.collectAsState()
    val isShuffle by AudioPlaybackManager.isShuffle.collectAsState()
    val sleepTimerSeconds by AudioPlaybackManager.sleepTimerRemainingSeconds.collectAsState()

    var showSleepTimerMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.background,
        contentColor = theme.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = theme.separator.copy(alpha = 0.7f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "قائمة التشغيل",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${playlist.size} مقاطع • تشغيل بالخلفية",
                            color = theme.success,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { showSleepTimerMenu = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "مؤقت النوم",
                                tint = if (sleepTimerSeconds > 0) theme.success else theme.textSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = showSleepTimerMenu,
                            onDismissRequest = { showSleepTimerMenu = false },
                            modifier = Modifier.background(theme.cardSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("إيقاف مؤقت النوم", fontFamily = CairoFont, color = theme.textPrimary) },
                                onClick = {
                                    AudioPlaybackManager.setSleepTimer(0)
                                    showSleepTimerMenu = false
                                }
                            )
                            listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text("$mins دقيقة", fontFamily = CairoFont, color = theme.textPrimary) },
                                    onClick = {
                                        AudioPlaybackManager.setSleepTimer(mins)
                                        showSleepTimerMenu = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { AudioPlaybackManager.toggleShuffle() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "خلط المقاطع",
                            tint = if (isShuffle) theme.primary else theme.textSecondary
                        )
                    }

                    IconButton(
                        onClick = { AudioPlaybackManager.cycleRepeatMode() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            when (repeatMode) {
                                AudioRepeatMode.ONE -> Icons.Default.RepeatOne
                                AudioRepeatMode.ALL -> Icons.Default.Repeat
                                AudioRepeatMode.OFF -> Icons.Default.Repeat
                            },
                            contentDescription = "تكرار",
                            tint = if (repeatMode != AudioRepeatMode.OFF) theme.primary else theme.textSecondary
                        )
                    }
                }
            }

            if (sleepTimerSeconds > 0) {
                val mins = sleepTimerSeconds / 60
                val secs = sleepTimerSeconds % 60
                Text(
                    text = "مؤقت النوم نشط: ${String.format(Locale.US, "%02d:%02d", mins, secs)} متبقية",
                    color = theme.success,
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            currentTrack?.let { track ->
                AppleCard(cornerRadius = 18.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                theme.primary.copy(alpha = 0.22f),
                                                theme.accent.copy(alpha = 0.16f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (onOpenStudioWithTrack != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                AppleSecondaryButton(
                                    text = "للاستوديو",
                                    onClick = {
                                        onOpenStudioWithTrack(track)
                                        onDismiss()
                                    },
                                    icon = Icons.Default.MovieFilter,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LuxuryAudioTimelineBar(
                            currentPositionMs = currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            isPlaying = isPlaying,
                            onSeek = { AudioPlaybackManager.seekTo(context, it) }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        AccurateAudioCountdownHud(
                            currentPositionMs = currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            isPlaying = isPlaying
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Surface(
                                onClick = {
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
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = theme.primary.copy(alpha = 0.14f),
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        "${playbackSpeed}x",
                                        color = theme.primary,
                                        fontSize = 12.sp,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                                    AudioPlaybackManager.seekTo(context, target)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "تراجع 10 ثوان", tint = theme.textPrimary)
                            }

                            IconButton(
                                onClick = { AudioPlaybackManager.previous(context) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "المقطع السابق", tint = theme.textPrimary)
                            }

                            val playPauseFabBg by animateColorAsState(
                                targetValue = if (isPlaying) theme.success else theme.primary,
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                label = "fab_play_pause_bg"
                            )

                            FloatingActionButton(
                                onClick = { AudioPlaybackManager.togglePlayState(context) },
                                containerColor = playPauseFabBg,
                                contentColor = Color.White,
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "تشغيل / إيقاف",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { AudioPlaybackManager.next(context) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "المقطع التالي", tint = theme.textPrimary)
                            }

                            IconButton(
                                onClick = {
                                    val target = (currentPositionMs + 10000L).coerceAtMost(totalDurationMs)
                                    AudioPlaybackManager.seekTo(context, target)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "تقديم 10 ثوان", tint = theme.textPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "طابور المقاطع",
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (playlist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "لا توجد مقاطع في قائمة التشغيل حالياً",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(playlist) { index, track ->
                        val isCurrent = index == currentIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clickable { AudioPlaybackManager.playTrack(context, track) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCurrent) theme.primary.copy(alpha = 0.12f) else theme.cardSurface,
                            border = BorderStroke(
                                if (isCurrent) 1.dp else 0.5.dp,
                                if (isCurrent) theme.primary else theme.separator.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isCurrent) theme.primary else theme.textSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = if (isCurrent) theme.primary else theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • ${track.category}",
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isCurrent && isPlaying) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "يعمل الآن",
                                        tint = theme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                IconButton(
                                    onClick = { AudioPlaybackManager.removeFromPlaylist(track.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "إزالة",
                                        tint = theme.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
