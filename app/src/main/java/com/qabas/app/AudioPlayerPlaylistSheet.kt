package com.qabas.app

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
import com.qabas.app.ui.theme.*

/**
 * نافذة قائمة التشغيل الاحترافية (Playlist Bottom Sheet) مع مميزات متقدمة:
 * - إعادة وترتيب وحذف من الطابور
 * - الوضع العشوائي والوضع التكراري (الكل، واحد، إيقاف)
 * - مؤقت النوم الذكي (Sleep Timer)
 * - مؤشر التشغيل بالخلفية الآمن
 * - شريط زمني رقمي فاخر للتقديم والتأخير
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlaylistBottomSheet(
    onDismiss: () -> Unit,
    onOpenStudioWithTrack: ((PlayableAudioTrack) -> Unit)? = null
) {
    val context = LocalContext.current
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

    val isDarkTheme by ThemeManager.isDarkTheme.collectAsState()

    val surfaceColor = if (isDarkTheme) Color(0xFF111726) else Color(0xFFFFFFFF)
    val cardColor = if (isDarkTheme) Color(0xFF1A2234) else Color(0xFFF1F5F9)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val subTextColor = if (isDarkTheme) TextSecondary else Color(0xFF64748B)

    var showSleepTimerMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(44.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = GoldPrimary.copy(alpha = 0.5f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "قائمة التشغيل الذكية",
                            color = textColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${playlist.size} مقاطع • يدعم التشغيل بالخلفية",
                            color = EmeraldGreen,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sleep Timer Button
                    Box {
                        IconButton(onClick = { showSleepTimerMenu = true }) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "مؤقت النوم",
                                tint = if (sleepTimerSeconds > 0) EmeraldGreen else subTextColor
                            )
                        }
                        DropdownMenu(
                            expanded = showSleepTimerMenu,
                            onDismissRequest = { showSleepTimerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("إيقاف مؤقت النوم", fontFamily = CairoFont) },
                                onClick = {
                                    AudioPlaybackManager.setSleepTimer(0)
                                    showSleepTimerMenu = false
                                }
                            )
                            listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text("$mins دقيقة", fontFamily = CairoFont) },
                                    onClick = {
                                        AudioPlaybackManager.setSleepTimer(mins)
                                        showSleepTimerMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Shuffle Button
                    IconButton(
                        onClick = { AudioPlaybackManager.toggleShuffle() }
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "خلط المقاطع",
                            tint = if (isShuffle) GoldPrimary else subTextColor
                        )
                    }

                    // Repeat Button
                    IconButton(
                        onClick = { AudioPlaybackManager.cycleRepeatMode() }
                    ) {
                        Icon(
                            when (repeatMode) {
                                AudioRepeatMode.ONE -> Icons.Default.RepeatOne
                                AudioRepeatMode.ALL -> Icons.Default.Repeat
                                AudioRepeatMode.OFF -> Icons.Default.Repeat
                            },
                            contentDescription = "تكرار",
                            tint = if (repeatMode != AudioRepeatMode.OFF) GoldPrimary else subTextColor
                        )
                    }
                }
            }

            if (sleepTimerSeconds > 0) {
                val mins = sleepTimerSeconds / 60
                val secs = sleepTimerSeconds % 60
                Text(
                    text = "مؤقت النوم نشط: ${String.format("%02d:%02d", mins, secs)} متبقية",
                    color = EmeraldGreen,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Currently Playing Card with Timeline & Controls
            currentTrack?.let { track ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                ) {
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
                                        Brush.linearGradient(listOf(GoldPrimary.copy(alpha = 0.3f), AiCyan.copy(alpha = 0.2f)))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = textColor,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = subTextColor,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp
                                )
                            }

                            // Use in Studio Button
                            if (onOpenStudioWithTrack != null) {
                                OutlinedButton(
                                    onClick = {
                                        onOpenStudioWithTrack(track)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, GoldPrimary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.MovieFilter, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("اعتماد للاستوديو", color = GoldPrimary, fontSize = 11.sp, fontFamily = CairoFont)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Luxury Timeline Seekbar
                        LuxuryAudioTimelineBar(
                            currentPositionMs = currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            isPlaying = isPlaying,
                            onSeek = { AudioPlaybackManager.seekTo(context, it) }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Accurate Live Countdown & Elapsed Timer HUD
                        AccurateAudioCountdownHud(
                            currentPositionMs = currentPositionMs,
                            totalDurationMs = totalDurationMs,
                            isPlaying = isPlaying
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Playback Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Speed Chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GoldPrimary.copy(alpha = 0.15f))
                                    .clickable {
                                        val nextSpeed = when (playbackSpeed) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            2.0f -> 0.75f
                                            else -> 1.0f
                                        }
                                        AudioPlaybackManager.setPlaybackSpeed(nextSpeed)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${playbackSpeed}x",
                                    color = GoldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 10s Rewind
                            IconButton(onClick = {
                                val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                                AudioPlaybackManager.seekTo(context, target)
                            }) {
                                Icon(Icons.Default.Replay10, contentDescription = "تراجع 10 ثوان", tint = textColor)
                            }

                            // Previous Track
                            IconButton(onClick = { AudioPlaybackManager.previous(context) }) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "المقطع السابق", tint = textColor)
                            }

                            // Play / Pause FAB (#play-pause-btn)
                            val playPauseFabBg by animateColorAsState(
                                targetValue = if (isPlaying) Color(0xFF10B981) else GoldPrimary,
                                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                                label = "fab_play_pause_bg"
                            )
                            val playPauseFabIconTint by animateColorAsState(
                                targetValue = if (isPlaying) Color.White else DeepSlate,
                                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                                label = "fab_play_pause_icon"
                            )

                            FloatingActionButton(
                                onClick = { AudioPlaybackManager.togglePlayState(context) },
                                containerColor = playPauseFabBg,
                                contentColor = playPauseFabIconTint,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "تشغيل / إيقاف",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Next Track
                            IconButton(onClick = { AudioPlaybackManager.next(context) }) {
                                Icon(Icons.Default.SkipNext, contentDescription = "المقطع التالي", tint = textColor)
                            }

                            // 10s Forward
                            IconButton(onClick = {
                                val target = (currentPositionMs + 10000L).coerceAtMost(totalDurationMs)
                                AudioPlaybackManager.seekTo(context, target)
                            }) {
                                Icon(Icons.Default.Forward10, contentDescription = "تقديم 10 ثوان", tint = textColor)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "طابور المقاطع القادمة:",
                color = subTextColor,
                fontFamily = CairoFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Playlist Queue Items
            if (playlist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد مقاطع في قائمة التشغيل حالياً", color = subTextColor, fontFamily = CairoFont, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(playlist) { index, track ->
                        val isCurrent = index == currentIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { AudioPlaybackManager.playTrack(context, track) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) GoldPrimary.copy(alpha = 0.15f) else cardColor,
                            border = if (isCurrent) BorderStroke(1.dp, GoldPrimary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isCurrent) GoldPrimary else subTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(22.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = if (isCurrent) GoldPrimary else textColor,
                                        fontFamily = CairoFont,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • ${track.category}",
                                        color = subTextColor,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp
                                    )
                                }

                                if (isCurrent && isPlaying) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = "يعمل الآن",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                IconButton(
                                    onClick = { AudioPlaybackManager.removeFromPlaylist(track.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "إزالة",
                                        tint = subTextColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
