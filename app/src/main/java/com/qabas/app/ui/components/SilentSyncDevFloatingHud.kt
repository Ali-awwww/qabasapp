package com.qabas.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.SilentReelPrefetcher
import com.qabas.app.SilentReelStorageManager
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ReelSafetyGuardrails

/**
 * 🛠️ SilentSyncDevFloatingHud (V2 - Advanced Developer Edition)
 * لوحة تشخيصية متقدمة لمراقبة وضع الأثر والتحكم اليدوي.
 */
@Composable
fun SilentSyncDevFloatingHud(isDeveloper: Boolean) {
    if (!isDeveloper) return

    val context = LocalContext.current
    val syncState by SilentReelPrefetcher.syncState.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    val isOnline = ReelSafetyGuardrails.isSupabaseCallsAllowed()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepSlate.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .animateContentSize()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color.Green else Color.Red)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AL-ATHAR DEV HUD",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = GoldPrimary
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Summary (Always visible)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Status: ${syncState.lastMessage}",
                    fontFamily = CairoFont,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = GoldPrimary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Storage Stats
                    HudDetailItem(Icons.Default.Storage, "Storage", formatBytes(syncState.totalStorageSize))
                    HudDetailItem(Icons.Default.VideoLibrary, "Downloaded", "${syncState.downloadedCount} Reels")
                    HudDetailItem(Icons.Default.Folder, "Sandbox", syncState.storagePath.takeLast(30))

                    if (syncState.isSyncing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SyncProgressSection(syncState)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DevActionButton(
                            text = "Sync Now",
                            icon = Icons.Default.Refresh,
                            color = GoldPrimary,
                            modifier = Modifier.weight(1f)
                        ) {
                            SilentReelPrefetcher.forceSync(context)
                        }
                        
                        DevActionButton(
                            text = "Clear All",
                            icon = Icons.Default.DeleteForever,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.weight(1f)
                        ) {
                            SilentReelStorageManager.clearAllSilentReels(context)
                            SilentReelPrefetcher.forceSync(context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncProgressSection(state: SilentReelPrefetcher.PrefetchState) {
    Column {
        Text(
            text = "Downloading: ${state.currentFileName}",
            fontFamily = CairoFont,
            fontSize = 9.sp,
            color = GoldPrimary
        )
        val progress = if (state.totalBytes > 0) state.downloadedBytes.toFloat() / state.totalBytes else 0f
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(4.dp).padding(vertical = 4.dp),
            color = GoldPrimary,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${(progress * 100).toInt()}% | ${formatBytes(state.downloadedBytes)}",
                fontFamily = CairoFont,
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "${formatBytes(state.speedBps)}/s",
                fontFamily = CairoFont,
                fontSize = 8.sp,
                color = GoldPrimary
            )
        }
    }
}

@Composable
private fun DevActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, fontFamily = CairoFont, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun HudDetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = GoldPrimary.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label: ",
            fontFamily = CairoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            fontFamily = CairoFont,
            fontSize = 10.sp,
            color = Color.White,
            maxLines = 1
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
