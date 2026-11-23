package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * مركز تنزيل وإدارة النماذج المحلية للعمل بدون إنترنت (حوار — كما كان).
 * المحتوى نفسه في [OnDeviceModelsContent] ليُعاد في تبويب «النماذج» (12.5).
 */
@Composable
fun OnDeviceModelDownloadDialog(
    context: Context,
    onModelActivated: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        OnDeviceModelsContent(
            context = context,
            onModelActivated = onModelActivated,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp)
        )
    }
}

/**
 * محتوى مركز النماذج المحلية — يُستعمل كحوار (أعلاه) وكتبويب «النماذج»
 * (المرتبة 4) في قسم فايب كودينج. منطق التنزيل/الحذف/التبديل/التصدير/
 * الاستيراد كاملاً في [OnDeviceModelManager] كما هو — لا إعادة كتابة هنا.
 *
 * قيد صدق ملزم: المحرك المحلي لا يعمل في بناء CI الافتراضي
 * (CMakeLists.txt: LLAMA_REAL=OFF والـ stub يعيد 0، ولا LLAMA_REAL في
 * android-ci.yml) — فالتبويب يعرض حالة المحرك بصدق عبر
 * [LlamaCppService.isEngineAvailable]، وإن كان غير متاح عُطّل تفعيل
 * الوضع المحلي برسالة صادقة؛ ولا يُفعَّل LLAMA_REAL=ON في CI هنا.
 */
@Composable
fun OnDeviceModelsContent(
    context: Context,
    onModelActivated: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize(),
    /** في التبويب لا زر إغلاق — التبويب نفسه هو الحاوية. */
    showCloseButton: Boolean = true
) {
    val downloadState by OnDeviceModelManager.downloadState.collectAsState()

    var isOfflineMode by remember { mutableStateOf(OnDeviceModelManager.isOfflineModeActive(context)) }
    var selectedModel by remember { mutableStateOf(OnDeviceModelManager.getSelectedModel(context)) }
    var refreshKey by remember { mutableStateOf(0) }
    // (12.16) نموذج محلي بانتظار تأكيد الحذف عبر الحوار أدناه.
    var modelPendingDelete by remember { mutableStateOf<OnDeviceModelInfo?>(null) }
    // حالة المحرك الحقيقية (محاولة تحميل llama_jni مرة واحدة، مخزنة في الخدمة).
    val engineAvailable = remember { LlamaCppService.isEngineAvailable() }
    val theme = QabasTheme.current

    GlassmorphicSurface(
            modifier = modifier,
            backgroundColor = theme.background.copy(alpha = 0.96f),
            borderColor = theme.primary.copy(alpha = 0.5f)
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
                            color = theme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = theme.primary,
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
                                color = theme.textPrimary
                            )
                            Text(
                                text = "تعمل محلياً في الهاتف بدون إنترنت وبدون مفتاح API",
                                fontSize = 10.sp,
                                color = theme.textSecondary
                            )
                        }
                    }

                    if (showCloseButton) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = theme.textSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // (12.5) حالة المحرك بصدق: إن كان غير متاح عُطّل تفعيل الوضع المحلي.
                if (!engineAvailable) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = theme.warning.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, theme.warning.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "المحرك المحلي غير متاح في هذه النسخة (بناء CI) — التنزيل والإدارة " +
                                "متاحان، لكن تفعيل الوضع المحلي معطّل حتى يُبنى التطبيق مع LLAMA_REAL=ON.",
                            fontSize = 11.sp,
                            color = theme.gold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // (12.4) بطاقة «قدرة جهازك»: قياس حي (RAM كلية/متاحة عبر
                // ActivityManager.MemoryInfo + تخزين حر/كلي عبر StatFs + نوى)
                // + النموذج المقترح. لا أرقام هنا لم تُقس.
                val deviceCapability = remember(refreshKey) { VibeDeviceCapability.measure(context) }
                val recommendedModel = remember(deviceCapability) {
                    VibeDeviceCapability.recommendModel(deviceCapability)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.cardSurface,
                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "قدرة جهازك (قياس حي)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            TextButton(
                                onClick = { refreshKey++ },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("إعادة القياس", fontSize = 10.sp, color = theme.primary)
                            }
                        }
                        Text(
                            text = "الذاكرة: ${formatDeviceBytes(deviceCapability.totalRamBytes)} كلية • " +
                                "${formatDeviceBytes(deviceCapability.availableRamBytes)} متاحة الآن",
                            fontSize = 11.sp,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "التخزين: ${formatDeviceBytes(deviceCapability.freeStorageBytes)} حر من أصل " +
                                "${formatDeviceBytes(deviceCapability.totalStorageBytes)} • النوى: ${deviceCapability.cpuCores}",
                            fontSize = 11.sp,
                            color = theme.textPrimary
                        )
                        Text(
                            text = if (recommendedModel != null) {
                                "النموذج المقترح لجهازك: ${recommendedModel.name} — الأقدر بين ما يعمل على جهازك الآن."
                            } else {
                                "لا يوجد نموذج يعمل على هذا الجهاز حالياً — حرّر ذاكرة أو مساحة تخزين ثم أعد القياس."
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (recommendedModel != null) theme.success else theme.destructive
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Switch Strip (سحابي vs محلي)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.cardSurface,
                    border = BorderStroke(1.dp, if (isOfflineMode) theme.primary else theme.separator),
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
                                tint = if (isOfflineMode) theme.primary else theme.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isOfflineMode) "وضع الوكيل: محلي بدون إنترنت 📴" else "وضع الوكيل: سحابي عبر المفتاح 🌐",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOfflineMode) theme.primary else theme.textPrimary
                                )
                                Text(
                                    text = if (isOfflineMode) "يعتمد على النماذج المنزلة محلياً في الجهاز" else "يعتمد على مفاتيح API (Gemini / Groq)",
                                    fontSize = 9.sp,
                                    color = theme.textSecondary
                                )
                            }
                        }

                        Switch(
                            checked = isOfflineMode,
                            onCheckedChange = { enabled ->
                                if (enabled && !engineAvailable) {
                                    Toast.makeText(context, "المحرك المحلي غير متاح في هذه النسخة (بناء CI) — لا يمكن تفعيل الوضع المحلي", Toast.LENGTH_LONG).show()
                                    return@Switch
                                }
                                if (enabled && !OnDeviceModelManager.hasAnyInstalledModel(context)) {
                                    Toast.makeText(context, "الرجاء تنزيل أحد النماذج أدناه أولاً لتفعيل الوضع المحلي", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                isOfflineMode = enabled
                                OnDeviceModelManager.setOfflineModeActive(context, enabled)
                                onModelActivated()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = theme.primary,
                                checkedTrackColor = theme.primary.copy(alpha = 0.4f),
                                uncheckedThumbColor = theme.textTertiary,
                                uncheckedTrackColor = theme.secondarySurface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "النماذج البرمجية الجاهزة للتنزيل والتشغيل:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.gold
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
                            // (12.4) النماذج التي لا يعمل جهازها تُعطَّل مع سبب صادق.
                            val deviceFits = VibeDeviceCapability.fitsModel(deviceCapability, model)
                            val fitReason = if (deviceFits) null
                                else VibeDeviceCapability.fitReason(deviceCapability, model)
                            OnDeviceModelCard(
                                context = context,
                                model = model,
                                isSelected = selectedModel.id == model.id,
                                downloadState = downloadState,
                                deviceFits = deviceFits,
                                fitReason = fitReason,
                                isDeviceRecommended = recommendedModel?.id == model.id,
                                onDownload = {
                                    // الخدمة الأمامية: عدّاد إشعار + يعمل بالخلفية حتى مع الخروج
                                    ModelDownloadService.start(context, model.id)
                                    Toast.makeText(context, "بدأ التنزيل في الخلفية — تابع العدّاد في الإشعارات 📥", Toast.LENGTH_LONG).show()
                                },
                                onDownloadSuccess = {
                                    selectedModel = model
                                    refreshKey++
                                    if (engineAvailable) {
                                        isOfflineMode = true
                                        OnDeviceModelManager.setOfflineModeActive(context, true)
                                        onModelActivated()
                                        Toast.makeText(context, "تم تنزيل وتفعيل ${model.name} بنجاح ✅", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "تم تنزيل ${model.name} — التفعيل المحلي غير متاح في هذه النسخة (بناء CI)", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onCancelDownload = {
                                    ModelDownloadService.cancel(context)
                                    OnDeviceModelManager.cancelDownload(context, model)
                                    Toast.makeText(context, "تم إلغاء التنزيل", Toast.LENGTH_SHORT).show()
                                },
                                onSelectAsActive = {
                                    OnDeviceModelManager.setSelectedModel(context, model.id)
                                    selectedModel = model
                                    if (engineAvailable) {
                                        isOfflineMode = true
                                        OnDeviceModelManager.setOfflineModeActive(context, true)
                                        onModelActivated()
                                        Toast.makeText(context, "تم تعيين ${model.name} كنموذج نشط للوكيل ⭐", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "تم اختيار ${model.name} — التفعيل المحلي غير متاح في هذه النسخة (بناء CI)", Toast.LENGTH_LONG).show()
                                    }
                                },
                                // (12.16) الحذف المباشر بلا تأكيد كان مدمراً بصمت —
                                // يفتح حوار تأكيد أولاً، والتنفيذ في الحوار أدناه.
                                onDelete = { modelPendingDelete = model }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Close Button (في الحوار فقط — التبويب بلا زر إغلاق)
                if (showCloseButton) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondarySurface, contentColor = theme.primary)
                    ) {
                        Text("تم وإغلاق", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // (12.16) حوار تأكيد حقيقي قبل حذف نموذج محلي (ملف GGUF كبير).
                modelPendingDelete?.let { model ->
                    AlertDialog(
                        onDismissRequest = { modelPendingDelete = null },
                        title = { Text("حذف نموذج محلي؟", fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "سيُحذف «${model.name}» (${model.sizeFormatted}) من الجهاز نهائياً، " +
                                    "وستحتاج إلى إعادة تنزيله لاستخدامه. هل أنت متأكد؟"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val deleted = OnDeviceModelManager.deleteModel(context, model)
                                if (deleted) {
                                    refreshKey++
                                    selectedModel = OnDeviceModelManager.getSelectedModel(context)
                                    isOfflineMode = OnDeviceModelManager.isOfflineModeActive(context) && OnDeviceModelManager.hasAnyInstalledModel(context)
                                    OnDeviceModelManager.setOfflineModeActive(context, isOfflineMode)
                                    onModelActivated()
                                    Toast.makeText(context, "تم حذف ${model.name} وتحرير المساحة 🗑️", Toast.LENGTH_SHORT).show()
                                }
                                modelPendingDelete = null
                            }) { Text("حذف نهائي", fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { modelPendingDelete = null }) { Text("إلغاء") }
                        }
                    )
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
    onDelete: () -> Unit,
    onDownloadSuccess: () -> Unit = {},
    /** (12.4) ملاءمة الجهاز: false تعني تعطيل التنزيل/الاستيراد/التفعيل مع السبب. */
    deviceFits: Boolean = true,
    fitReason: String? = null,
    isDeviceRecommended: Boolean = false
) {
    // نجاح الخلفية: الخدمة الأمامية تُكمل التنزيل وخروج التطبيق لا يلغيه
    // الحارس يمنع إعادة الإطلاق عند كل إعادة تركيب والحالة ما زالت Success
    var successHandledFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(downloadState) {
        if (downloadState is ModelDownloadState.Success &&
            downloadState.modelId == model.id &&
            successHandledFor != model.id
        ) {
            successHandledFor = model.id
            onDownloadSuccess()
        }
        if (downloadState !is ModelDownloadState.Success) {
            successHandledFor = null
        }
    }
    val isInstalled = remember(model.id) { OnDeviceModelManager.isModelInstalled(context, model) }
    val theme = QabasTheme.current
    val isThisDownloading = downloadState is ModelDownloadState.Downloading && downloadState.modelId == model.id
    || downloadState is ModelDownloadState.Interrupted && downloadState.modelId == model.id
    val badgeColor = Color(model.badgeColorHex)
    val coroutineScope = rememberCoroutineScope()
    var isFileBusy by remember { mutableStateOf(false) }
    var fileProgress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isFileBusy = true
            fileProgress = 0L to model.sizeBytes
            coroutineScope.launch {
                val res = OnDeviceModelManager.importModel(context, model, uri) { done, total ->
                    fileProgress = done to total
                }
                isFileBusy = false
                fileProgress = null
                Toast.makeText(
                    context,
                    when (res) {
                        is OnDeviceModelManager.ModelFileResult.Ok -> res.message
                        is OnDeviceModelManager.ModelFileResult.Err -> res.message
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = theme.cardSurface,
        border = BorderStroke(
            width = if (isSelected && isInstalled) 1.5.dp else 1.dp,
            color = if (isSelected && isInstalled) theme.primary else theme.separator
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
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = theme.secondarySurface
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
                    tint = theme.textSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "متطلبات الذاكرة: ${model.ramRequirement}",
                    fontSize = 10.sp,
                    color = theme.textPrimary
                )

                if (model.isRecommended) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = theme.success.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "موصى به للهواتف ⭐",
                            fontSize = 9.sp,
                            color = theme.success,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                if (isDeviceRecommended) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = theme.primary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "الأنسب لجهازك ⭐",
                            fontSize = 9.sp,
                            color = theme.primary,
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
                color = theme.textSecondary,
                lineHeight = 14.sp
            )

            // (12.4) سبب صادق لعدم عمل النموذج على هذا الجهاز.
            if (!deviceFits && fitReason != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = theme.destructive.copy(alpha = 0.1f),
                    border = BorderStroke(0.6.dp, theme.destructive.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا يعمل على هذا الجهاز: $fitReason التنزيل والتفعيل معطّلان.",
                        fontSize = 10.sp,
                        color = theme.destructive,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Download State / Controls
            if (isThisDownloading) {
                val state: ModelDownloadState.Downloading = when (downloadState) {
                    is ModelDownloadState.Downloading -> downloadState
                    is ModelDownloadState.Interrupted -> ModelDownloadState.Downloading(
                        modelId = downloadState.modelId,
                        progress = if (downloadState.totalBytes > 0) downloadState.downloadedBytes.toFloat() / downloadState.totalBytes else 0f,
                        downloadedBytes = downloadState.downloadedBytes,
                        totalBytes = downloadState.totalBytes
                    )
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
                            color = theme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.speedKbps > 0) {
                            Text(
                                text = "${state.speedKbps} KB/s",
                                fontSize = 9.sp,
                                color = theme.textSecondary,
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
                        color = theme.primary,
                        trackColor = theme.secondarySurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    when (downloadState) {
                        is ModelDownloadState.Downloading -> {
                            OutlinedButton(
                                onClick = onCancelDownload,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                border = BorderStroke(1.dp, theme.destructive),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = theme.destructive, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إلغاء التنزيل", fontSize = 10.sp, color = theme.destructive)
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
                                border = BorderStroke(1.dp, theme.primary),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = theme.primary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("استكمال التنزيل", fontSize = 10.sp, color = theme.primary)
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
                    color = theme.success.copy(alpha = 0.1f),
                    border = BorderStroke(0.6.dp, theme.success.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = theme.success, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSelected) "النموذج النشط حالياً للوكيل ✅" else "مثبت في ذاكرة الهاتف",
                                fontSize = 10.sp,
                                color = theme.success,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isSelected) {
                            TextButton(
                                onClick = onSelectAsActive,
                                enabled = deviceFits,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("تفعيل كالنموذج النشط", fontSize = 10.sp, color = theme.primary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            isFileBusy = true
                            fileProgress = 0L to model.sizeBytes
                            coroutineScope.launch {
                                val res = OnDeviceModelManager.exportModel(context, model) { done, total ->
                                    fileProgress = done to total
                                }
                                isFileBusy = false
                                fileProgress = null
                                Toast.makeText(
                                    context,
                                    when (res) {
                                        is OnDeviceModelManager.ModelFileResult.Ok -> res.message
                                        is OnDeviceModelManager.ModelFileResult.Err -> res.message
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = !isFileBusy,
                        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Upload, contentDescription = null, tint = theme.primary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("تصدير نسخة احتياطية", fontSize = 9.sp, color = theme.primary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        border = BorderStroke(1.dp, theme.separator),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("حذف الملف لتحرير المساحة", fontSize = 9.sp, color = theme.textSecondary)
                    }
                }
                // شريط تقدم النسخ (تصدير) داخل البطاقة
                if (fileProgress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val (done, total) = fileProgress!!
                    LinearProgressIndicator(
                        progress = { if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = theme.primary,
                        trackColor = theme.secondarySurface
                    )
                }
            } else {
                // Not Installed: Download Button
                Button(
                    onClick = onDownload,
                    enabled = deviceFits,
                    colors = ButtonDefaults.buttonColors(containerColor = badgeColor, contentColor = Color.Black),
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

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { importPicker.launch("*/*") },
                    enabled = !isFileBusy && deviceFits,
                    border = BorderStroke(1.dp, theme.separator),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "أو استيراد ملف محفوظ (${model.fileName}) 📂",
                        fontSize = 10.sp,
                        color = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // شريط تقدم النسخ (استيراد) داخل البطاقة
                if (fileProgress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val (done, total) = fileProgress!!
                    LinearProgressIndicator(
                        progress = { if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = theme.primary,
                        trackColor = theme.secondarySurface
                    )
                }
            }
        }
    }
}
