package com.qabas.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.SilentReelPrefetcher
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary

/**
 * 🛠️ SilentSyncDevFloatingHud
 * لوحة طافية للمطور تعرض حالة المزامنة الصامتة ومسارات التخزين.
 * تظهر فقط للمطورين.
 */
@Composable
fun SilentSyncDevFloatingHud(isDeveloper: Boolean) {
    if (!isDeveloper) return

    val syncState by SilentReelPrefetcher.syncState.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DeepSlate.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clickable { isExpanded = !isExpanded }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeveloperMode,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dev: Silent Sync HUD",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = GoldPrimary
                        )
                    }
                    if (syncState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = GoldPrimary
                        )
                    }
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = GoldPrimary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    HudDetailItem(
                        icon = Icons.Default.Info,
                        label = "Path",
                        value = syncState.storagePath.takeLast(40)
                    )
                    
                    if (syncState.isSyncing) {
                        HudDetailItem(
                            icon = Icons.Default.Download,
                            label = "Syncing",
                            value = syncState.currentFileName
                        )
                        
                        val progress = if (syncState.totalBytes > 0) 
                            syncState.downloadedBytes.toFloat() / syncState.totalBytes 
                        else 0f
                        
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .padding(vertical = 4.dp),
                            color = GoldPrimary,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        
                        Text(
                            text = "${(progress * 100).toInt()}% | ${formatBytes(syncState.downloadedBytes)} / ${formatBytes(syncState.totalBytes)}",
                            fontFamily = CairoFont,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Speed: ${formatBytes(syncState.speedBps)}/s",
                            fontFamily = CairoFont,
                            fontSize = 9.sp,
                            color = GoldPrimary
                        )
                    } else {
                        Text(
                            text = "Status: Idle / Connected",
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            color = Color.Green.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudDetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: ",
            fontFamily = CairoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontFamily = CairoFont,
            fontSize = 9.sp,
            color = Color.White,
            maxLines = 1
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
