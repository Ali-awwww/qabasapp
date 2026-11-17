package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.AppleListRow
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.launch

@Composable
fun OfflineAiSettingsSection() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()

    var selectedModel by remember { mutableStateOf(OnDeviceModelManager.getSelectedModel(context)) }
    var isOfflineMode by remember { mutableStateOf(OnDeviceModelManager.isOfflineModeActive(context)) }
    var isModelReady by remember(selectedModel.id) {
        mutableStateOf(OnDeviceModelManager.isModelInstalled(context, selectedModel))
    }
    var statusMessage by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }

    val downloadState by OnDeviceModelManager.downloadState.collectAsState()
    val currentDownload = downloadState as? ModelDownloadState.Downloading
    val isDownloadingForSelected = currentDownload != null && currentDownload.modelId == selectedModel.id
    val isInterruptedForSelected = (downloadState as? ModelDownloadState.Interrupted)?.modelId == selectedModel.id

    LaunchedEffect(downloadState, selectedModel.id) {
        isModelReady = OnDeviceModelManager.isModelInstalled(context, selectedModel)
        when (val st = downloadState) {
            is ModelDownloadState.Success -> if (st.modelId == selectedModel.id) {
                isModelReady = OnDeviceModelManager.isModelInstalled(context, selectedModel)
                statusMessage = "تم تثبيت ${selectedModel.name} بنجاح"
            }
            is ModelDownloadState.Error -> if (st.modelId == selectedModel.id) {
                statusMessage = st.message
            }
            is ModelDownloadState.Interrupted -> if (st.modelId == selectedModel.id) {
                statusMessage = "توقف التنزيل عند ${st.downloadedBytes / (1024 * 1024)} MB — يمكنك الاستكمال"
            }
            else -> Unit
        }
    }

    AppleCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الذكاء الاصطناعي المحلي (بدون إنترنت)",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "نموذج GGUF محلي بخصوصية كاملة ودون الحاجة لمفتاح API",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOfflineMode) "الوضع المحلي مفعّل" else "التوجيه الذكي السحابي/المحلي",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = when {
                            isOfflineMode && isModelReady -> "${selectedModel.name} نشط ويعمل أوفلاين"
                            isModelReady -> "النموذج مثبت وجاهز للتفعيل"
                            else -> "يتطلب تنزيل نموذج محلي أولاً"
                        },
                        color = if (isOfflineMode && isModelReady) theme.success else theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isOfflineMode,
                    onCheckedChange = { enabled ->
                        if (enabled && !OnDeviceModelManager.hasAnyInstalledModel(context)) {
                            statusMessage = "يجب تنزيل نموذج واحد على الأقل قبل تفعيل الوضع المحلي"
                            return@Switch
                        }
                        isOfflineMode = enabled
                        OnDeviceModelManager.setOfflineModeActive(context, enabled)
                        statusMessage = if (enabled) "تم تفعيل التشغيل المحلي الكامل" else "تم التبديل إلى الوضع السحابي مع احتياطي محلي"
                    },
                    modifier = Modifier.testTag("offline_ai_mode_switch")
                )
            }

            // Active / Selected model summary card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.55f else 0.45f),
                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showModelSelector = !showModelSelector }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isModelReady) Icons.Default.CheckCircle else Icons.Default.OfflineBolt,
                        contentDescription = null,
                        tint = if (isModelReady) theme.success else theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedModel.name,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "${selectedModel.sizeFormatted} • ${selectedModel.ramRequirement} • ${if (isModelReady) "مثبت" else "غير مثبت"}",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.5.sp
                        )
                    }
                    Text(
                        text = if (showModelSelector) "إخفاء" else "تغيير",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (showModelSelector) {
                AppleCard(containerColor = theme.secondarySurface.copy(alpha = 0.35f)) {
                    Column {
                        OnDeviceModelManager.availableModels.forEachIndexed { index, model ->
                            val installed = OnDeviceModelManager.isModelInstalled(context, model)
                            AppleListRow(
                                title = model.name,
                                subtitle = "${model.sizeFormatted} • ${model.ramRequirement}",
                                trailingText = if (installed) "مثبت ✓" else if (model.id == selectedModel.id) "محدد" else null,
                                showChevron = false,
                                onClick = {
                                    selectedModel = model
                                    OnDeviceModelManager.setSelectedModel(context, model.id)
                                    isModelReady = installed
                                    showModelSelector = false
                                }
                            )
                            if (index < OnDeviceModelManager.availableModels.lastIndex) {
                                AppleInsetDivider()
                            }
                        }
                    }
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    color = theme.primary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp
                )
            }

            when {
                isDownloadingForSelected && currentDownload != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { currentDownload.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = theme.primary,
                            trackColor = theme.secondarySurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "جاري التنزيل... ${(currentDownload.progress * 100).toInt()}%",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (currentDownload.speedKbps > 0) "${currentDownload.speedKbps} KB/s" else "",
                                color = theme.primary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
                            )
                        }
                        AppleSecondaryButton(
                            text = "إلغاء التنزيل",
                            tint = theme.destructive,
                            onClick = { OnDeviceModelManager.cancelDownload(context, selectedModel) }
                        )
                    }
                }

                isInterruptedForSelected -> {
                    ApplePrimaryButton(
                        text = "استكمال تنزيل ${selectedModel.name}",
                        icon = Icons.Default.Refresh,
                        onClick = {
                            scope.launch {
                                statusMessage = "جاري استكمال التنزيل..."
                                val ok = OnDeviceModelManager.resumeDownload(context, selectedModel)
                                isModelReady = OnDeviceModelManager.isModelInstalled(context, selectedModel)
                                if (ok) {
                                    statusMessage = "تم اكتمال تنزيل ${selectedModel.name}"
                                }
                            }
                        }
                    )
                }

                !isModelReady -> {
                    ApplePrimaryButton(
                        text = "تنزيل ${selectedModel.name} (${selectedModel.sizeFormatted})",
                        icon = Icons.Default.CloudDownload,
                        modifier = Modifier.testTag("offline_ai_download_button"),
                        onClick = {
                            scope.launch {
                                statusMessage = "بدء تنزيل ${selectedModel.name}..."
                                OnDeviceModelManager.downloadModel(context, selectedModel)
                                isModelReady = OnDeviceModelManager.isModelInstalled(context, selectedModel)
                            }
                        }
                    )
                    Text(
                        text = "يتطلب اتصال إنترنت للتنزيل الأولي فقط، وبعدها يعمل محلياً دون إنترنت.",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp
                    )
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isOfflineMode) {
                            ApplePrimaryButton(
                                text = "تفعيل الوضع المحلي",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    OnDeviceModelManager.setOfflineModeActive(context, true)
                                    isOfflineMode = true
                                    statusMessage = "تم تفعيل الوضع المحلي"
                                }
                            )
                        }
                        AppleSecondaryButton(
                            text = "حذف النموذج",
                            icon = Icons.Default.DeleteOutline,
                            tint = theme.destructive,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val deleted = OnDeviceModelManager.deleteModel(context, selectedModel)
                                if (deleted) {
                                    isModelReady = false
                                    if (!OnDeviceModelManager.hasAnyInstalledModel(context)) {
                                        isOfflineMode = false
                                        OnDeviceModelManager.setOfflineModeActive(context, false)
                                    }
                                    Toast.makeText(context, "تم حذف ملف النموذج", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
