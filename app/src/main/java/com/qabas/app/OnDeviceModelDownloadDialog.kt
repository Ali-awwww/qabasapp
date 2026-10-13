package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qabas.app.ui.components.GlassmorphicSurface
import kotlinx.coroutines.launch

/**
 * مركز تنزيل وإدارة النماذج المحلية للعمل بدون إنترنت
 */
@Composable
fun OnDeviceModelDownloadDialog(
    context: Context,
    onModelActivated: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val downloadState by OnDeviceModelManager.downloadState.collectAsState()

    var isOfflineMode by remember { mutableStateOf(OnDeviceModelManager.isOfflineModeActive(context)) }
    var selectedModel by remember { mutableStateOf(OnDeviceModelManager.getSelectedModel(context)) }
    var refreshKey by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp),
            backgroundColor = Color(0xFF090E17).copy(alpha = 0.96f),
            borderColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "مركز النماذج المحلية (On-Device)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "تعمل محلياً في الهاتف بدون إنترنت وبدون مفتاح API",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Switch Strip (سحابي vs محلي)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, if (isOfflineMode) Color(0xFF00E5FF) else Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOfflineMode) Icons.Default.WifiOff else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (isOfflineMode) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isOfflineMode) "وضع الوكيل: محلي بدون إنترنت 📴" else "وضع الوكيل: سحابي عبر المفتاح 🌐",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOfflineMode) Color(0xFF00E5FF) else Color.White
                                )
                                Text(
                                    text = if (isOfflineMode) "يعتمد على النماذج المنزلة محلياً في الجهاز" else "يعتمد على مفاتيح API (Gemini / Groq)",
                                    fontSize = 9.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Switch(
                            checked = isOfflineMode,
                            onCheckedChange = { enabled ->
                                if (enabled && !OnDeviceModelManager.hasAnyInstalledModel(context)) {
                                    Toast.makeText(context, "الرجاء تنزيل أحد النماذج أدناه أولاً لتفعيل الوضع المحلي", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                isOfflineMode = enabled
                                OnDeviceModelManager.setOfflineModeActive(context, enabled)
                                onModelActivated()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E5FF),
                                checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "النماذج البرمجية الجاهزة للتنزيل والتشغيل:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE5B842)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Models List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(OnDeviceModelManager.availableModels) { model ->
                        key(model.id, refreshKey) {
                            OnDeviceModelCard(
                                context = context,
                                model = model,
                                isSelected = selectedModel.id == model.id,
                                downloadState = downloadState,
                                onDownload = {
                                    coroutineScope.launch {
                                        val success = OnDeviceModelManager.downloadModel(context, model)
                                        if (success) {
                                            selectedModel = model
                                            isOfflineMode = true
                                            OnDeviceModelManager.setOfflineModeActive(context, true)
                                            refreshKey++
                                            onModelActivated()
                                            Toast.makeText(context, "تم تنزيل وتفعيل ${model.name} بنجاح ✅", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onCancelDownload = {
                                    OnDeviceModelManager.cancelDownload()
                                    Toast.makeText(context, "تم إلغاء التنزيل", Toast.LENGTH_SHORT).show()
                                },
                                onSelectAsActive = {
                                    OnDeviceModelManager.setSelectedModel(context, model.id)
                                    selectedModel = model
                                    isOfflineMode = true
                                    OnDeviceModelManager.setOfflineModeActive(context, true)
                                    onModelActivated()
                                    Toast.makeText(context, "تم تعيين ${model.name} كنموذج نشط للوكيل ⭐", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    val deleted = OnDeviceModelManager.deleteModel(context, model)
                                    if (deleted) {
                                        refreshKey++
                                        selectedModel = OnDeviceModelManager.getSelectedModel(context)
                                        isOfflineMode = OnDeviceModelManager.isOfflineModeActive(context) && OnDeviceModelManager.hasAnyInstalledModel(context)
                                        OnDeviceModelManager.setOfflineModeActive(context, isOfflineMode)
                                        onModelActivated()
                                        Toast.makeText(context, "تم حذف ${model.name} وتحرير المساحة 🗑️", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color(0xFF00E5FF))
                ) {
                    Text("تم وإغلاق", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * بطاقة عرض وتنزيل النموذج المحلي
 */
@Composable
fun OnDeviceModelCard(
    context: Context,
    model: OnDeviceModelInfo,
    isSelected: Boolean,
    downloadState: ModelDownloadState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onSelectAsActive: () -> Unit,
    onDelete: () -> Unit
) {
    val isInstalled = remember(model.id) { OnDeviceModelManager.isModelInstalled(context, model) }
    val isThisDownloading = downloadState is ModelDownloadState.Downloading && downloadState.modelId == model.id
    || downloadState is ModelDownloadState.Interrupted && downloadState.modelId == model.id
    val badgeColor = Color(model.badgeColorHex)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(
            width = if (isSelected && isInstalled) 1.5.dp else 1.dp,
            color = if (isSelected && isInstalled) Color(0xFF00E5FF) else Color(0xFF334155)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Name, Badge, Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.2f),
                        border = BorderStroke(0.6.dp, badgeColor)
                    ) {
                        Text(
                            text = model.sizeFormatted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = model.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = model.tag,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Specs Row: RAM & Recommended
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "متطلبات الذاكرة: ${model.ramRequirement}",
                    fontSize = 10.sp,
                    color = Color(0xFFCBD5E1)
                )

                if (model.isRecommended) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "موصى به للهواتف ⭐",
                            fontSize = 9.sp,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Description
            Text(
                text = model.description,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Download State / Controls
            if (isThisDownloading) {
                val state: ModelDownloadState.Downloading = when (downloadState) {
                    is ModelDownloadState.Downloading -> downloadState
                    else -> throw IllegalStateException("Unexpected download state: $downloadState")
                }
                val downloadedMb = state.downloadedBytes / (1024 * 1024)
                val totalMb = state.totalBytes / (1024 * 1024)
                val progressPercent = (state.progress * 100).toInt()

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "جاري التنزيل: $downloadedMb MB من أصل $totalMb MB ($progressPercent%)",
                            fontSize = 10.sp,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                        if (state.speedKbps > 0) {
                            Text(
                                text = "${state.speedKbps} KB/s",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00E5FF),
                        trackColor = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    when (downloadState) {
                        is ModelDownloadState.Downloading -> {
                            OutlinedButton(
                                onClick = onCancelDownload,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إلغاء التنزيل", fontSize = 10.sp, color = Color(0xFFEF4444))
                            }
                        }
                        is ModelDownloadState.Interrupted -> {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        OnDeviceModelManager.resumeDownload(context, model)
                                    }
                                    Toast.makeText(context, "استكمال التنزيل...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("استكمال التنزيل", fontSize = 10.sp, color = Color(0xFF00E5FF))
                            }
                        }
                        else -> Unit
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            } else if (isInstalled) {
                // Installed State
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                    border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSelected) "النموذج النشط حالياً للوكيل ✅" else "مثبت في ذاكرة الهاتف",
                                fontSize = 10.sp,
                                color = Color(0xFF6EE7B7),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isSelected) {
                            TextButton(
                                onClick = onSelectAsActive,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("تفعيل كالنموذج النشط", fontSize = 10.sp, color = Color(0xFF00E5FF))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("حذف الملف لتحرير المساحة", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    }
                }
            } else {
                // Not Installed: Download Button
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = badgeColor, contentColor = Color(0xFF070B14)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تنزيل النموذج (${model.sizeFormatted}) 📥",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
