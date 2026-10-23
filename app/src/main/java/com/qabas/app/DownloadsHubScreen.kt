package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.CardSurface
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.RobotoMonoFont
import com.qabas.app.ui.theme.TextPrimary
import com.qabas.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun StrictBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = GoldPrimary
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = CutCornerShape(2.dp),
        border = BorderStroke(0.6.dp, color),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private val SpatialDeckShape = CutCornerShape(
    topStart = 22.dp,
    topEnd = 6.dp,
    bottomEnd = 22.dp,
    bottomStart = 6.dp
)
private val SpatialCardShape = CutCornerShape(
    topStart = 14.dp,
    topEnd = 4.dp,
    bottomEnd = 14.dp,
    bottomStart = 4.dp
)
private val SpatialInsetShape = CutCornerShape(
    topStart = 8.dp,
    topEnd = 2.dp,
    bottomEnd = 8.dp,
    bottomStart = 2.dp
)
private val SpatialButtonShape = CutCornerShape(
    topStart = 9.dp,
    topEnd = 3.dp,
    bottomEnd = 9.dp,
    bottomStart = 3.dp
)

private enum class DownloadCategory(
    val tabIndex: Int,
    val label: String,
    val icon: ImageVector,
    val accent: Color,
    val tabWeight: Float
) {
    Models(0, "نماذج GGUF", Icons.Default.Memory, Color(0xFF00E5FF), 1f),
    Updates(1, "تحديثات التطبيق", Icons.Default.SystemUpdate, Color(0xFF10B981), 1.15f),
    Reels(2, "الريلز", Icons.Default.SlowMotionVideo, Color(0xFFA855F7), 0.9f),
    Media(3, "صوتيات", Icons.Default.Audiotrack, GoldPrimary, 0.85f)
}

private data class ActiveDownloadEntry(
    val id: String,
    val title: String,
    val category: DownloadCategory,
    val progress: Float,
    val statusText: String,
    val detailText: String = "",
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

/**
 * حاوية قيادة واحدة تجمع مؤشر التخزين ووضع المحرك وقنوات التنزيل
 * بدل بطاقات متناثرة — بهدف إيقاع بصري هندسي واحد.
 */
@Composable
private fun SpatialControlDeck(
    metrics: StorageMetrics,
    activeDownloads: List<ActiveDownloadEntry>,
    isOfflineAiActive: Boolean,
    selectedModel: OnDeviceModelInfo,
    hasInstalledModel: Boolean,
    onToggleOfflineAi: (Boolean) -> Unit,
    onOpenAssistant: () -> Unit,
    onCleanCache: () -> Unit,
    onOpenCategory: (Int) -> Unit,
    categoryCounts: Map<DownloadCategory, Int>,
    visibleCategories: List<DownloadCategory>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isUpdateActive: Boolean,
    isCheckingUpdate: Boolean,
    onCheckUpdates: () -> Unit
) {
    Surface(
        color = CardSurface.copy(alpha = 0.92f),
        shape = SpatialDeckShape,
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 26.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(GoldPrimary, Color(0xFF00E5FF))
                            )
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "لوحة التحكم المركزية",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "CONTROL DECK // STORAGE · ENGINE · CHANNELS",
                        fontFamily = RobotoMonoFont,
                        fontSize = 8.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .testTag("deck_update_checking"),
                        color = Color(0xFF10B981),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else if (DownloadCategory.Updates !in visibleCategories) {
                    IconButton(
                        onClick = onCheckUpdates,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                            .testTag("deck_check_updates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "فحص التحديثات",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                StrictBadge(text = "LIVE", color = Color(0xFF10B981))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                GoldPrimary.copy(alpha = 0.65f),
                                Color(0xFF00E5FF).copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SpatialStorageIndicatorWidget(
                    metrics = metrics,
                    onCleanCache = onCleanCache,
                    embedded = true
                )

                if (activeDownloads.isNotEmpty()) {
                    SpatialDeckDivider()
                    ActiveDownloadsPanel(
                        entries = activeDownloads,
                        onOpenCategory = onOpenCategory
                    )
                }

                SpatialDeckDivider()

                OfflineAiModeBanner(
                    isActive = isOfflineAiActive,
                    selectedModel = selectedModel,
                    hasInstalledModel = hasInstalledModel,
                    onToggle = onToggleOfflineAi,
                    onOpenAssistant = onOpenAssistant,
                    embedded = true
                )

                if (visibleCategories.isNotEmpty()) {
                    SpatialDeckDivider()

                    Text(
                        text = "قنوات التنزيل",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )

                    SpatialDownloadsTabsRow(
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        visibleCategories = visibleCategories,
                        categoryCounts = categoryCounts,
                        isUpdateActive = isUpdateActive,
                        embedded = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SpatialDeckDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(Color(0xFF00E5FF).copy(alpha = 0.8f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}

/**
 * لوحة التنزيلات النشطة الموحّدة — تعرض كل عنصر قيد التنزيل مع شارة تصنيفه،
 * وتجمع المصادر الأربعة (GGUF / التحديث / الريلز / الصوتيات) في مكان واحد.
 */
@Composable
private fun ActiveDownloadsPanel(
    entries: List<ActiveDownloadEntry>,
    onOpenCategory: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Downloading,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "التنزيلات النشطة",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.weight(1f))
            StrictBadge(
                text = "${entries.size}",
                color = Color(0xFF00E5FF)
            )
        }

        entries.forEach { entry ->
            Surface(
                color = entry.category.accent.copy(alpha = 0.07f),
                shape = SpatialInsetShape,
                border = BorderStroke(0.8.dp, entry.category.accent.copy(alpha = 0.32f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCategory(entry.category.tabIndex) }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StrictBadge(
                            text = entry.category.label,
                            color = entry.category.accent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.title,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (entry.actionLabel != null && entry.onAction != null) {
                            val actionLabel = entry.actionLabel
                            val action = entry.onAction
                            TextButton(
                                onClick = action,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = actionLabel,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = entry.category.accent,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { entry.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CutCornerShape(2.dp)),
                        color = entry.category.accent,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.statusText,
                            fontFamily = CairoFont,
                            fontSize = 9.5.sp,
                            color = entry.category.accent,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (entry.detailText.isNotBlank()) {
                            Text(
                                text = entry.detailText,
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * شاشة مركز التنزيلات وإدارة نماذج الذكاء الاصطناعي الأوفلاين (GGUF) وفيديوهات الريلز والوسائط
 * مصممة وفق أسلوب: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsHubScreen(
    onBack: () -> Unit,
    onOpenReels: () -> Unit = {},
    onOpenAiAssistant: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // تهيئة البيانات
    LaunchedEffect(Unit) {
        DownloadsHubManager.init(context)
    }

    val storageMetrics by DownloadsHubManager.storageMetrics.collectAsState()
    val downloadedReels by DownloadsHubManager.downloadedReels.collectAsState()
    val downloadedMedia by DownloadsHubManager.downloadedMedia.collectAsState()
    val activeReelDownloads by DownloadsHubManager.activeReelDownloads.collectAsState()
    val activeMediaDownloads by DownloadsHubManager.activeMediaDownloads.collectAsState()
    val modelDownloadState by OnDeviceModelManager.downloadState.collectAsState()
    val appUpdateDownload by DownloadsHubManager.appUpdateDownload.collectAsState()
    val isCheckingUpdate by DownloadsHubManager.isCheckingUpdate.collectAsState()

    var isOfflineAiActive by remember {
        mutableStateOf(OnDeviceModelManager.isOfflineModeActive(context))
    }
    var selectedModel by remember {
        mutableStateOf(OnDeviceModelManager.getSelectedModel(context))
    }

    // 0: نماذج الذكاء (GGUF), 1: تحديثات التطبيق (OTA), 2: فيديوهات الريلز, 3: الصوتيات والوسائط
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialogs
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<OnDeviceModelInfo?>(null) }
    var reelToDelete by remember { mutableStateOf<DownloadedReelItem?>(null) }
    var showQuickReelDownloadDialog by remember { mutableStateOf(false) }
    var showDeleteUpdateDialog by remember { mutableStateOf(false) }
    var quickReelUrlInput by remember { mutableStateOf("") }
    var quickReelTitleInput by remember { mutableStateOf("") }
    var showTestInferenceDialog by remember { mutableStateOf<OnDeviceModelInfo?>(null) }

    val activeModelState = modelDownloadState
    val installedModels = OnDeviceModelManager.availableModels.filter { model ->
        OnDeviceModelManager.isModelInstalled(context, model)
    }
    val activeModel = when (val state = activeModelState) {
        is ModelDownloadState.Downloading -> OnDeviceModelManager.availableModels.find { it.id == state.modelId }
        is ModelDownloadState.Interrupted -> OnDeviceModelManager.availableModels.find { it.id == state.modelId }
        is ModelDownloadState.Error -> OnDeviceModelManager.availableModels.find { it.id == state.modelId }
        else -> null
    }

    val categoryCounts = mapOf(
        DownloadCategory.Models to (installedModels.map { it.id } + listOfNotNull(activeModel?.id)).toSet().size,
        DownloadCategory.Updates to if (appUpdateDownload != null || isCheckingUpdate) 1 else 0,
        DownloadCategory.Reels to (downloadedReels.map { it.id } + activeReelDownloads.keys).toSet().size,
        DownloadCategory.Media to (downloadedMedia.map { it.id } + activeMediaDownloads.keys).toSet().size
    )
    val visibleCategories = DownloadCategory.entries.filter { (categoryCounts[it] ?: 0) > 0 }

    LaunchedEffect(visibleCategories, selectedTab) {
        if (visibleCategories.isNotEmpty() && visibleCategories.none { it.tabIndex == selectedTab }) {
            selectedTab = visibleCategories.first().tabIndex
        }
    }

    val activeDownloads = buildList {
        activeModel?.let { model ->
            when (val state = activeModelState) {
                is ModelDownloadState.Downloading -> add(
                    ActiveDownloadEntry(
                        id = "model_${model.id}",
                        title = model.name,
                        category = DownloadCategory.Models,
                        progress = state.progress,
                        statusText = "جارٍ تنزيل النموذج",
                        detailText = "${state.progress.coerceIn(0f, 1f).times(100).toInt()}% • " +
                            "${StorageMetrics.formatBytes(state.downloadedBytes)} / ${StorageMetrics.formatBytes(state.totalBytes)}",
                        actionLabel = "إلغاء",
                        onAction = {
                            ModelDownloadService.cancel(context)
                            OnDeviceModelManager.cancelDownload(context, model)
                            DownloadsHubManager.refreshStorageMetrics(context)
                        }
                    )
                )
                is ModelDownloadState.Interrupted -> {
                    val progress = if (state.totalBytes > 0) {
                        (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f)
                    } else 0f
                    add(
                        ActiveDownloadEntry(
                            id = "model_${model.id}",
                            title = model.name,
                            category = DownloadCategory.Models,
                            progress = progress,
                            statusText = "متوقف — اضغط للاستئناف",
                            detailText = "${StorageMetrics.formatBytes(state.downloadedBytes)} / ${StorageMetrics.formatBytes(state.totalBytes)}",
                            actionLabel = "استئناف",
                            onAction = { ModelDownloadService.start(context, model.id) }
                        )
                    )
                }
                is ModelDownloadState.Error -> add(
                    ActiveDownloadEntry(
                        id = "model_${model.id}",
                        title = model.name,
                        category = DownloadCategory.Models,
                        progress = 0f,
                        statusText = "فشل تنزيل النموذج",
                        detailText = state.message.take(40),
                        actionLabel = "إعادة",
                        onAction = { ModelDownloadService.start(context, model.id) }
                    )
                )
                else -> Unit
            }
        }

        appUpdateDownload?.let { update ->
            if (update.isDownloading || update.isError) {
                add(
                    ActiveDownloadEntry(
                        id = "update_${update.versionCode}",
                        title = "تحديث التطبيق ${update.versionName}",
                        category = DownloadCategory.Updates,
                        progress = update.progress,
                        statusText = if (update.isError) "فشل تنزيل التحديث" else "جارٍ تنزيل التحديث",
                        detailText = "${update.percentInt}% • ${update.formattedDownloaded} / ${update.formattedSize}",
                        actionLabel = if (update.isError) "إعادة" else "إلغاء",
                        onAction = {
                            if (update.isError) {
                                DownloadsHubManager.checkAndStartAutoUpdate(context, force = true)
                            } else {
                                DownloadsHubManager.cancelUpdateDownload(context)
                            }
                        }
                    )
                )
            }
        }

        activeReelDownloads.values.forEach { progress ->
            add(
                ActiveDownloadEntry(
                    id = "reel_${progress.reelId}",
                    title = progress.title,
                    category = DownloadCategory.Reels,
                    progress = progress.progress.coerceIn(0f, 1f),
                    statusText = if (progress.isError) "فشل تنزيل الريل" else "جارٍ تنزيل الريل",
                    detailText = if (progress.totalBytes > 0) {
                        "${StorageMetrics.formatBytes(progress.downloadedBytes)} / ${StorageMetrics.formatBytes(progress.totalBytes)}"
                    } else progress.errorMessage?.take(40).orEmpty(),
                    actionLabel = if (progress.isError) "إزالة" else "إلغاء",
                    onAction = { DownloadsHubManager.cancelReelDownload(progress.reelId) }
                )
            )
        }

        activeMediaDownloads.values.forEach { progress ->
            add(
                ActiveDownloadEntry(
                    id = "media_${progress.reelId}",
                    title = progress.title,
                    category = DownloadCategory.Media,
                    progress = progress.progress.coerceIn(0f, 1f),
                    statusText = if (progress.isError) "فشل تنزيل الصوت" else "جارٍ تنزيل الصوت",
                    detailText = if (progress.totalBytes > 0) {
                        "${StorageMetrics.formatBytes(progress.downloadedBytes)} / ${StorageMetrics.formatBytes(progress.totalBytes)}"
                    } else progress.errorMessage?.take(40).orEmpty(),
                    actionLabel = if (progress.isError) "إزالة" else "إلغاء",
                    onAction = { DownloadsHubManager.cancelMediaDownload(progress.reelId) }
                )
            )
        }
    }

    Scaffold(
        containerColor = DeepSlate,
        bottomBar = bottomBar,
        topBar = {
            Surface(
                color = CardSurface.copy(alpha = 0.95f),
                border = BorderStroke(0.6.dp, GoldPrimary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                                .testTag("downloads_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "مركز التنزيلات والنماذج",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (appUpdateDownload?.isDownloading == true) {
                                    StrictBadge(
                                        text = "تحديث قيد التنزيل ${appUpdateDownload?.percentInt}٪ 🚀",
                                        color = Color(0xFF00E5FF),
                                        modifier = Modifier.clickable { selectedTab = 1 }
                                    )
                                } else if (appUpdateDownload?.isCompleted == true) {
                                    StrictBadge(
                                        text = "تحديث جاهز للتثبيت ⚡",
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.clickable { selectedTab = 1 }
                                    )
                                } else {
                                    StrictBadge(
                                        text = "SPATIAL HUD ✦",
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                            }
                            Text(
                                text = if (appUpdateDownload?.isDownloading == true)
                                    "AUTO UPDATE // GITHUB RELEASES // ${appUpdateDownload?.versionName}"
                                else
                                    "OFFLINE LLM & MEDIA ENGINE // GGUF",
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.sp,
                                color = if (appUpdateDownload?.isDownloading == true) Color(0xFF00E5FF) else TextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // زر تحديث الذاكرة والكاش
                    IconButton(
                        onClick = {
                            DownloadsHubManager.refreshStorageMetrics(context)
                            Toast.makeText(context, "تم تحديث قياسات الذاكرة التخزينية 🔄", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .testTag("refresh_storage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // لوحة تحكم واحدة: التخزين + التنزيلات النشطة + الوضع المحلي + قنوات التنزيل
            item(key = "spatial_control_deck") {
                SpatialControlDeck(
                    metrics = storageMetrics,
                    activeDownloads = activeDownloads,
                    isOfflineAiActive = isOfflineAiActive,
                    selectedModel = selectedModel,
                    hasInstalledModel = OnDeviceModelManager.hasAnyInstalledModel(context),
                    onToggleOfflineAi = { newState ->
                        if (newState && !OnDeviceModelManager.hasAnyInstalledModel(context)) {
                            Toast.makeText(context, "يرجى تنزيل أحد نماذج GGUF أولاً لتفعيل الوضع المحلي!", Toast.LENGTH_LONG).show()
                        } else {
                            isOfflineAiActive = newState
                            OnDeviceModelManager.setOfflineModeActive(context, newState)
                            Toast.makeText(
                                context,
                                if (newState) "تم تفعيل رادار الذكاء الاصطناعي بدون إنترنت ⚡" else "تمت العودة للنموذج السحابي ☁️",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onOpenAssistant = onOpenAiAssistant,
                    onCleanCache = { showClearCacheDialog = true },
                    onOpenCategory = { selectedTab = it },
                    categoryCounts = categoryCounts,
                    visibleCategories = visibleCategories,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    isUpdateActive = appUpdateDownload?.isDownloading == true || appUpdateDownload?.isCompleted == true,
                    isCheckingUpdate = isCheckingUpdate,
                    onCheckUpdates = {
                        DownloadsHubManager.checkAndStartAutoUpdate(context, force = true)
                        Toast.makeText(context, UpdateChannelFacade.checkingToast(context), Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // محتويات التبويب المختار — تُعرض فقط للتصنيفات غير الفارغة
            if (visibleCategories.isEmpty()) {
                item(key = "empty_all_downloads") {
                    EmptyDownloadsPlaceholder(
                        title = "لا توجد تنزيلات أو تحديثات حالياً",
                        subtitle = "ابدأ بتنزيل نموذج GGUF أو ريل، وستظهر تصنيفاتك هنا تلقائياً مع حالة كل عنصر.",
                        actionText = "فتح الوكيل الذكي 🤖",
                        onAction = onOpenAiAssistant
                    )
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        // تبويب نماذج LLM الأوفلاين (GGUF) — يعرض المكتمل فقط؛ النشط في اللوحة أعلاه
                        if (installedModels.isEmpty()) {
                            item(key = "empty_models") {
                                EmptyDownloadsPlaceholder(
                                    title = if (activeModel != null) "جارٍ تنزيل النموذج..." else "لا توجد نماذج منزّلة حالياً",
                                    subtitle = if (activeModel != null) {
                                        "تابع تقدم التنزيل وإجراءاته من لوحة التنزيلات النشطة أعلاه؛ سيظهر النموذج هنا فور اكتماله."
                                    } else {
                                        "نزّل نماذج GGUF من شاشة الوكيل الذكي، وستظهر هنا فوراً للتشغيل بدون إنترنت."
                                    },
                                    actionText = "فتح الوكيل الذكي 🤖",
                                    onAction = onOpenAiAssistant
                                )
                            }
                        } else {
                            items(
                                items = installedModels,
                                key = { "model_${it.id}" }
                            ) { model ->
                                val isSelected = selectedModel.id == model.id
                                val installedSize = OnDeviceModelManager.getInstalledFileSize(context, model)

                                GgufModelCard(
                                    model = model,
                                    isInstalled = true,
                                    isSelected = isSelected,
                                    installedSizeBytes = installedSize,
                                    downloadState = modelDownloadState,
                                    onDownloadClick = {
                                        ModelDownloadService.start(context, model.id)
                                        Toast.makeText(context, "بدأ تنزيل نموذج ${model.name} مع إشعار خلفي 📥", Toast.LENGTH_SHORT).show()
                                    },
                                    onCancelClick = {
                                        ModelDownloadService.cancel(context)
                                        OnDeviceModelManager.cancelDownload(context, model)
                                        DownloadsHubManager.refreshStorageMetrics(context)
                                        Toast.makeText(context, "تم إلغاء تنزيل ${model.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    onSelectAsDefault = {
                                        selectedModel = model
                                        OnDeviceModelManager.setSelectedModel(context, model.id)
                                        Toast.makeText(context, "تم تعيين ${model.name} كنموذج افتراضي ✅", Toast.LENGTH_SHORT).show()
                                    },
                                    onDeleteClick = {
                                        modelToDelete = model
                                    },
                                    onTestPromptClick = {
                                        showTestInferenceDialog = model
                                    }
                                )
                            }
                        }
                    }
                    1 -> {
                        // تبويب تحديثات التطبيق (OTA Updates)
                        item(key = "app_updates_section") {
                            AppUpdatesHubSection(
                                updateItem = appUpdateDownload,
                                isChecking = isCheckingUpdate,
                                onCheckUpdates = {
                                    DownloadsHubManager.checkAndStartAutoUpdate(context, force = true)
                                    Toast.makeText(context, UpdateChannelFacade.checkingToast(context), Toast.LENGTH_SHORT).show()
                                },
                                onInstall = {
                                    val ok = DownloadsHubManager.installDownloadedUpdate(context)
                                    if (!ok) Toast.makeText(context, "تعذر فتح ملف التثبيت", Toast.LENGTH_SHORT).show()
                                },
                                onCancel = {
                                    DownloadsHubManager.cancelUpdateDownload(context)
                                    Toast.makeText(context, "تم إلغاء تنزيل التحديث", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteApk = {
                                    showDeleteUpdateDialog = true
                                }
                            )
                        }
                    }
                    2 -> {
                        // تبويب فيديوهات الريلز — المكتمل فقط؛ النشط في اللوحة أعلاه
                        item(key = "reels_header_actions") {
                            ReelsDownloadsHeader(
                                onOpenQuickDownload = { showQuickReelDownloadDialog = true },
                                onBrowseReels = onOpenReels
                            )
                        }

                        if (downloadedReels.isEmpty()) {
                            item(key = "empty_reels") {
                                EmptyDownloadsPlaceholder(
                                    title = if (activeReelDownloads.isNotEmpty()) "جارٍ تنزيل الريلز..." else "لا توجد فيديوهات ريلز منزّلة حالياً",
                                    subtitle = if (activeReelDownloads.isNotEmpty()) {
                                        "تابع التقدم من لوحة التنزيلات النشطة أعلاه؛ ستظهر الريلز هنا فور اكتمالها."
                                    } else {
                                        "يمكنك تنزيل أي ريل من قسم «ريلز قبس» أو إدخال رابطه المباشر لحفظه في الهاتف وتشغيله بدون إنترنت."
                                    },
                                    actionText = "استعراض خلاصات الريلز 🎬",
                                    onAction = onOpenReels
                                )
                            }
                        } else {
                            items(
                                items = downloadedReels,
                                key = { "saved_reel_${it.id}" }
                            ) { reel ->
                                DownloadedReelCard(
                                    reel = reel,
                                    onPlay = {
                                        playVideoLocally(context, reel.localFilePath)
                                    },
                                    onShare = {
                                        shareVideoFile(context, reel.localFilePath, reel.title)
                                    },
                                    onDelete = {
                                        reelToDelete = reel
                                    }
                                )
                            }
                        }
                    }
                    3 -> {
                        // تبويب الوسائط والصوتيات — المكتمل فقط؛ النشط في اللوحة أعلاه
                        if (downloadedMedia.isEmpty()) {
                            item(key = "empty_media") {
                                EmptyDownloadsPlaceholder(
                                    title = if (activeMediaDownloads.isNotEmpty()) "جارٍ تنزيل الصوتيات..." else "لا توجد ملفات صوتية أو وسائط منزلة",
                                    subtitle = if (activeMediaDownloads.isNotEmpty()) {
                                        "تابع التقدم من لوحة التنزيلات النشطة أعلاه؛ ستظهر الملفات هنا فور اكتمالها."
                                    } else {
                                        "أي تلاوة قرآنية، صوتيات مرافقة، أو تصاميم يتم تنزيلها ستظهر هنا بدقة عالية وإحصائيات حجم دقيقة."
                                    },
                                    actionText = "فتح مكتبة الصوتيات 🎙️",
                                    onAction = {
                                        Toast.makeText(context, "انتقل لمكتبة الصوتيات من القائمة الجانبية", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        } else {
                            items(
                                items = downloadedMedia,
                                key = { "saved_media_${it.id}" }
                            ) { media ->
                                DownloadedMediaCard(
                                    media = media,
                                    onDelete = {
                                        DownloadsHubManager.deleteDownloadedMedia(context, media.id)
                                        Toast.makeText(context, "تم حذف المقطع", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Dialogs
    // 1. تأكيد مسح الكاش
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text("تنظيف الملفات المؤقتة 🧹", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "سيتم مسح ملفات التخزين المؤقت والملفات الجزئية (.tmp) لتحرير مساحة الذاكرة دون المساس بالنماذج المكتملة أو الريلز المحفوظة.",
                    fontFamily = CairoFont,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val freed = DownloadsHubManager.clearTempCache(context)
                        showClearCacheDialog = false
                        Toast.makeText(context, "تم تحرير ${StorageMetrics.formatBytes(freed)} من الذاكرة بنجاح! ✨", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("تنظيف الآن", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("إلغاء", fontFamily = CairoFont, color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }

    // 2. تأكيد حذف نموذج GGUF
    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = {
                Text("حذف نموذج ${model.name}؟", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "هل أنت متأكد من حذف ملف النموذج (${model.sizeFormatted}) من الهاتف؟ ستحتاج لتنزيله مجدداً لاستخدامه بدون إنترنت.",
                    fontFamily = CairoFont,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = OnDeviceModelManager.deleteModel(context, model)
                        modelToDelete = null
                        DownloadsHubManager.refreshStorageMetrics(context)
                        if (deleted) {
                            Toast.makeText(context, "تم حذف النموذج وتحرير المساحة 🗑️", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("حذف الملف", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("إلغاء", fontFamily = CairoFont, color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }

    // 3. تأكيد حذف ريل
    reelToDelete?.let { reel ->
        AlertDialog(
            onDismissRequest = { reelToDelete = null },
            title = {
                Text("حذف الريل المنزّل؟", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "سيتم حذف فيديو «${reel.title}» (${reel.formattedSize}) من الذاكرة المحلية للجهاز.",
                    fontFamily = CairoFont,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        DownloadsHubManager.deleteDownloadedReel(context, reel.id)
                        reelToDelete = null
                        Toast.makeText(context, "تم حذف الفيديو بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("تأكيد الحذف", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reelToDelete = null }) {
                    Text("إلغاء", fontFamily = CairoFont, color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }

    // 4. نافذة التنزيل السريع لريل عبر الرابط
    if (showQuickReelDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showQuickReelDownloadDialog = false },
            title = {
                Text("تنزيل ريل أو فيديو مخصص 📥", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "أدخل عنوان ورابط الفيديو المباشر لبدء التنزيل وحفظه في مركز التنزيلات:",
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = quickReelTitleInput,
                        onValueChange = { quickReelTitleInput = it },
                        label = { Text("عنوان الفيديو أو الوصف", fontFamily = CairoFont) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = quickReelUrlInput,
                        onValueChange = { quickReelUrlInput = it },
                        label = { Text("رابط الفيديو (URL .mp4)", fontFamily = CairoFont) },
                        placeholder = { Text("https://example.com/video.mp4", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = quickReelUrlInput.trim()
                        val title = quickReelTitleInput.trim().ifBlank { "مقطع فيديو جديد" }
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            val id = "reel_${System.currentTimeMillis()}"
                            DownloadsHubManager.downloadReel(
                                context = context,
                                reelId = id,
                                title = title,
                                videoUrl = url
                            )
                            showQuickReelDownloadDialog = false
                            quickReelUrlInput = ""
                            quickReelTitleInput = ""
                            Toast.makeText(context, "بدأ تنزيل الفيديو في الخلفية 🚀", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "يرجى إدخال رابط فيديو صالح يبدأ بـ http:// أو https://", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("بدء التنزيل", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = DeepSlate)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickReelDownloadDialog = false }) {
                    Text("إلغاء", fontFamily = CairoFont, color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }

    // 5. حوار اختبار الاستدلال المحلي (Prompt Test Dialog)
    showTestInferenceDialog?.let { model ->
        LocalInferenceTestDialog(
            model = model,
            onDismiss = { showTestInferenceDialog = null }
        )
    }

    // 6. حوار تأكيد حذف ملف التحديث
    if (showDeleteUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteUpdateDialog = false },
            title = {
                Text("حذف ملف التحديث؟ 🗑️", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "سيتم حذف حزمة الـ APK المنزّلة لتحرير مساحة الذاكرة في الهاتف. يمكنك فحص وتنزيل التحديث مجدداً في أي وقت.",
                    fontFamily = CairoFont,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        DownloadsHubManager.deleteDownloadedUpdate(context)
                        showDeleteUpdateDialog = false
                        Toast.makeText(context, "تم حذف ملف التحديث وتحرير الذاكرة ✨", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("حذف الملف", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUpdateDialog = false }) {
                    Text("إلغاء", fontFamily = CairoFont, color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }
}

/**
 * ويدجت مؤشر سعة الذاكرة الفضائي مع شريط ألوان قطاعي
 */
@Composable
private fun SpatialStorageIndicatorWidget(
    metrics: StorageMetrics,
    onCleanCache: () -> Unit,
    embedded: Boolean = false
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = if (embedded) Modifier.fillMaxWidth() else Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // العنوان ومؤشر المساحة المتاحة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مؤشر التخزين المحلي",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StrictBadge(
                        text = "مباشر",
                        color = Color(0xFF10B981)
                    )
                }

                TextButton(
                    onClick = onCleanCache,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "تنظيف الكاش",
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // الشريط المتعدد الملون لسعة التخزين
            MultiSegmentStorageBar(metrics = metrics)

            // إحصائيات المقاييس — صفّان حتى لا تُضغط البطاقات على الشاشات الضيقة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                StorageStatPill(
                    label = "المتاح",
                    value = metrics.availableFormatted,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                StorageStatPill(
                    label = "إجمالي الهاتف",
                    value = metrics.totalFormatted,
                    color = GoldPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                StorageStatPill(
                    label = "نماذج GGUF",
                    value = metrics.llmModelsFormatted,
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                StorageStatPill(
                    label = "الريلز والوسائط",
                    value = metrics.reelsFormatted,
                    color = Color(0xFFA855F7),
                    modifier = Modifier.weight(1f)
                )
                if (metrics.updatesBytes > 0L) {
                    StorageStatPill(
                        label = "التحديثات",
                        value = metrics.updatesFormatted,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    if (embedded) {
        content()
    } else {
        Surface(
            color = CardSurface.copy(alpha = 0.85f),
            shape = SpatialCardShape,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * شريط سعة التخزين متعدد المقاطع الملونة
 */
@Composable
private fun MultiSegmentStorageBar(metrics: StorageMetrics) {
    val total = metrics.totalBytes.coerceAtLeast(1L).toFloat()
    val llmRatio = (metrics.llmModelsBytes / total).coerceIn(0f, 1f)
    val updatesRatio = (metrics.updatesBytes / total).coerceIn(0f, 1f)
    val reelsRatio = (metrics.reelsBytes / total).coerceIn(0f, 1f)
    val otherUsedRatio = ((metrics.usedBytes - metrics.totalQabasBytes).coerceAtLeast(0L) / total).coerceIn(0f, 1f)
    val freeRatio = (metrics.availableBytes / total).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CutCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (llmRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(llmRatio.coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(Color(0xFF00E5FF))
                    )
                }
                if (updatesRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(updatesRatio.coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(Color(0xFFF59E0B))
                    )
                }
                if (reelsRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(reelsRatio.coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(Color(0xFFA855F7))
                    )
                }
                if (otherUsedRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(otherUsedRatio.coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(Color(0xFF475569))
                    )
                }
                if (freeRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(freeRatio.coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(Color(0xFF10B981).copy(alpha = 0.6f))
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "مساحة قبس: ${metrics.totalQabasFormatted}",
                fontFamily = CairoFont,
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = "مساحة فارغة: ${metrics.availableFormatted}",
                fontFamily = CairoFont,
                fontSize = 10.5.sp,
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StorageStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = SpatialInsetShape,
        border = BorderStroke(0.6.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontFamily = CairoFont,
                fontSize = 9.sp,
                color = color,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = value,
                fontFamily = RobotoMonoFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * بنر تفعيل وضع الذكاء الاصطناعي الأوفلاين في الوكيل الذكي
 */
@Composable
private fun OfflineAiModeBanner(
    isActive: Boolean,
    selectedModel: OnDeviceModelInfo,
    hasInstalledModel: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenAssistant: () -> Unit,
    embedded: Boolean = false
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = if (embedded) {
                Modifier
                    .fillMaxWidth()
                    .clip(SpatialInsetShape)
                    .background(
                        if (isActive) Color(0xFF00E5FF).copy(alpha = 0.08f)
                        else Color.White.copy(alpha = 0.03f)
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            } else {
                Modifier.padding(14.dp)
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.OfflineBolt else Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFF00E5FF) else GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isActive) "الوضع المحلي: بدون إنترنت" else "وضع السحاب (Gemini)",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        StrictBadge(
                            text = if (isActive) "محلي" else "سحابي",
                            color = if (isActive) Color(0xFF00E5FF) else GoldPrimary
                        )
                    }
                    Text(
                        text = if (isActive) "النموذج النشط: ${selectedModel.name}" else "تحويل الرادار لتشغيل المهام بدون إنترنت وبدون مفاتيح API",
                        fontFamily = CairoFont,
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00E5FF),
                    checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.35f),
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color.DarkGray
                ),
                modifier = Modifier.testTag("offline_ai_toggle_switch")
            )
        }
    }
    if (embedded) {
        content()
    } else {
        Surface(
            color = if (isActive) Color(0xFF0F2E2E) else CardSurface.copy(alpha = 0.85f),
            shape = SpatialCardShape,
            border = BorderStroke(1.dp, if (isActive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * شريط التبويبات الفضائي
 */
@Composable
private fun SpatialDownloadsTabsRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    visibleCategories: List<DownloadCategory>,
    categoryCounts: Map<DownloadCategory, Int>,
    isUpdateActive: Boolean,
    embedded: Boolean = false
) {
    Row(
        modifier = if (embedded) {
            Modifier
                .fillMaxWidth()
                .clip(SpatialInsetShape)
                .background(CardSurface.copy(alpha = 0.45f))
                .padding(4.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .clip(SpatialCardShape)
                .background(CardSurface.copy(alpha = 0.6f))
                .padding(4.dp)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleCategories.forEach { category ->
                SpatialTabButton(
                    label = category.label,
                    count = if (category == DownloadCategory.Updates && isUpdateActive) {
                        "NEW ⚡"
                    } else {
                        "${categoryCounts[category] ?: 0}"
                    },
                    icon = category.icon,
                    isSelected = selectedTab == category.tabIndex,
                    activeColor = category.accent,
                    onClick = { onTabSelected(category.tabIndex) }
                )
            }
        }
    }
}

/**
 * بنر شريطي فضائي مباشر يعرض تقدم تنزيل التحديث من أي مكان داخل مركز التنزيلات
 */
@Composable
private fun SpatialLiveUpdateStickyBanner(
    updateItem: AppUpdateDownloadItem,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onOpenTab: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "updatePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = CardSurface.copy(alpha = 0.95f),
        shape = SpatialCardShape,
        border = BorderStroke(
            1.2.dp,
            if (updateItem.isCompleted) Color(0xFF10B981)
            else Color(0xFF00E5FF).copy(alpha = pulseAlpha)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTab() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (updateItem.isCompleted) Color(0xFF10B981).copy(alpha = 0.2f)
                                else Color(0xFF00E5FF).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (updateItem.isCompleted) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (updateItem.isCompleted) Color(0xFF10B981) else Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "تحديث التطبيق: ${updateItem.versionName}",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            StrictBadge(
                                text = if (updateItem.isCompleted) "جاهز للتثبيت ⚡" else "تنزيل تلقائي نشط 🚀",
                                color = if (updateItem.isCompleted) Color(0xFF10B981) else Color(0xFF00E5FF)
                            )
                        }
                        Text(
                            text = if (updateItem.isCompleted) "اكتمل التنزيل بنجاح — انقر هنا للتثبيت الفوري"
                            else "جاري التنزيل تلقائياً في الخلفية فور اكتشاف الإصدار الجديد",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // شريط التقدم
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { updateItem.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CutCornerShape(2.dp)),
                    color = if (updateItem.isCompleted) Color(0xFF10B981) else Color(0xFF00E5FF),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${updateItem.percentInt}% • ${updateItem.formattedDownloaded} من ${updateItem.formattedSize}",
                        fontFamily = RobotoMonoFont,
                        fontSize = 10.5.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    if (updateItem.isDownloading && updateItem.speedBytesPerSec > 0) {
                        Text(
                            text = "السرعة: ${updateItem.formattedSpeed}",
                            fontFamily = RobotoMonoFont,
                            fontSize = 10.5.sp,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // أزرار الإجراء السريع
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (updateItem.isCompleted) {
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = SpatialInsetShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تثبيت التحديث الآن ⚡",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                } else if (updateItem.isDownloading) {
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.7f)),
                        shape = SpatialInsetShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "إلغاء التنزيل",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}

/**
 * قسم تبويب تحديثات التطبيق (OTA Updates Hub Section)
 */
@Composable
private fun AppUpdatesHubSection(
    updateItem: AppUpdateDownloadItem?,
    isChecking: Boolean,
    onCheckUpdates: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDeleteApk: () -> Unit
) {
    val context = LocalContext.current
    val currentVersionName = remember { UpdateManager.getCurrentVersionName(context) }
    val currentVersionCode = remember { UpdateManager.getCurrentVersionCode(context) }
    var autoCheckHours by remember {
        mutableIntStateOf(UpdateManager.getAutoCheckIntervalHours(context))
    }
    val lastCheckTime = remember { UpdateManager.getLastCheckTime(context) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. بطاقة معلومات الإصدار الحالي
        Surface(
            color = CardSurface.copy(alpha = 0.85f),
            shape = SpatialCardShape,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "معلومات الإصدار وقناة التحديث",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                    }

                    StrictBadge(
                        text = "GITHUB OTA",
                        color = Color(0xFF00E5FF)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = SpatialInsetShape,
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "الإصدار المثبت حالياً",
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "v$currentVersionName",
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Build #$currentVersionCode",
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.sp,
                                color = GoldPrimary
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = SpatialInsetShape,
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = UpdateChannelFacade.channelLabel(context),
                                fontFamily = CairoFont,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            Text(
                                // اسم المستودع يظهر للمطور فقط — المستخدم العادي يرى اسم القناة
                                text = if (UpdateChannelFacade.isDeveloper(context)) {
                                    UpdateChannelFacade.effectiveRepo(context)
                                } else {
                                    UpdateChannelFacade.PUBLIC_CHANNEL_NAME
                                },
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color(0xFF00E5FF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = UpdateChannelFacade.checkSourceLabel(context),
                                fontFamily = CairoFont,
                                fontSize = 9.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                // زر فحص يدوي للتحديثات
                Button(
                    onClick = onCheckUpdates,
                    enabled = !isChecking,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = SpatialButtonShape
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = DeepSlate,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = UpdateChannelFacade.checkingButtonLabel(context),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                color = DeepSlate,
                                fontSize = 12.5.sp
                            )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = DeepSlate,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = UpdateChannelFacade.checkButtonLabel(context),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            color = DeepSlate,
                            fontSize = 12.5.sp
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // الفحص التلقائي: الحد الأدنى ساعتان — المدة المختارة تُحفظ فوراً
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "الفحص التلقائي كل $autoCheckHours ساعة",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lastCheckTime > 0L)
                                "آخر فحص: " + android.text.format.DateUtils.getRelativeTimeSpanString(
                                    lastCheckTime,
                                    System.currentTimeMillis(),
                                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                                )
                            else "لم يُفحص بعد",
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 4, 6, 12, 24).forEach { hours ->
                            val selected = autoCheckHours == hours
                            Surface(
                                color = if (selected) Color(0xFF00E5FF).copy(alpha = 0.16f)
                                    else Color.White.copy(alpha = 0.05f),
                                shape = SpatialInsetShape,
                                border = BorderStroke(
                                    0.7.dp,
                                    if (selected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(SpatialInsetShape)
                                    .clickable {
                                        autoCheckHours = UpdateManager.setAutoCheckIntervalHours(context, hours)
                                        Toast.makeText(
                                            context,
                                            "سيتم الفحص التلقائي كل $autoCheckHours ساعة",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            ) {
                                Text(
                                    text = "$hours س",
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = if (selected) Color(0xFF00E5FF) else TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "الحد الأدنى للفحص التلقائي ساعتان. الفحص اليدوي يعمل فوراً في أي وقت.",
                        fontFamily = CairoFont,
                        fontSize = 9.5.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // 1.5 لوحة تشخيص التحديث — للمطور فقط (تختفي تماماً عن المستخدم العادي)
        if (UpdateChannelFacade.isDeveloper(context)) {
            UpdateDiagnosticsPanel()
        }

        // 2. بطاقة حالة التحديث (قيد التنزيل أو مكتمل أو لا يوجد)
        if (updateItem != null) {
            Surface(
                color = CardSurface.copy(alpha = 0.9f),
                shape = SpatialCardShape,
                border = BorderStroke(
                    1.dp,
                    if (updateItem.isCompleted) Color(0xFF10B981)
                    else if (updateItem.isError) Color(0xFFEF4444)
                    else Color(0xFF00E5FF)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (updateItem.isCompleted) Color(0xFF10B981).copy(alpha = 0.2f)
                                        else Color(0xFF00E5FF).copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (updateItem.isCompleted) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = if (updateItem.isCompleted) Color(0xFF10B981) else Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "إصدار ${updateItem.versionName}",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "رقم البناء: ${updateItem.versionCode}",
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StrictBadge(
                                text = "تحديث",
                                color = Color(0xFF10B981)
                            )
                            StrictBadge(
                                text = when {
                                    updateItem.isCompleted -> "جاهز للتثبيت ✅"
                                    updateItem.isDownloading -> "جاري التنزيل تلقائياً 🚀"
                                    updateItem.isError -> "فشل التنزيل ⚠️"
                                    else -> "تحديث متوفر"
                                },
                                color = when {
                                    updateItem.isCompleted -> Color(0xFF10B981)
                                    updateItem.isError -> Color(0xFFEF4444)
                                    else -> Color(0xFF00E5FF)
                                }
                            )
                        }
                    }

                    // شريط التقدم
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { updateItem.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(9.dp)
                                .clip(CutCornerShape(2.dp)),
                            color = if (updateItem.isCompleted) Color(0xFF10B981) else Color(0xFF00E5FF),
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${updateItem.percentInt}% • تم تنزيل ${updateItem.formattedDownloaded} من ${updateItem.formattedSize}",
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                            if (updateItem.isDownloading && updateItem.speedBytesPerSec > 0) {
                                Text(
                                    text = "السرعة: ${updateItem.formattedSpeed}",
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.sp,
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // ملاحظات الإصدار (Release Notes)
                    if (updateItem.releaseNotes.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = SpatialInsetShape,
                            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "ملاحظات وتغييرات الإصدار:",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = GoldPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = updateItem.releaseNotes,
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // أزرار الإجراءات
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (updateItem.isCompleted) {
                            Button(
                                onClick = onInstall,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = SpatialInsetShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تثبيت التحديث الآن ⚡",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }

                            OutlinedButton(
                                onClick = onDeleteApk,
                                border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                shape = SpatialInsetShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else if (updateItem.isDownloading) {
                            OutlinedButton(
                                onClick = onCancel,
                                border = BorderStroke(0.8.dp, Color(0xFFEF4444)),
                                shape = SpatialInsetShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "إلغاء تنزيل التحديث ✖",
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // حالة: النسخة الحالية محدثة
            Surface(
                color = CardSurface.copy(alpha = 0.8f),
                shape = SpatialCardShape,
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "تطبيقك محدّث لأحدث إصدار رسمي ✨",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )

                    Text(
                        text = UpdateChannelFacade.aboutText(context, currentVersionName),
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 3. كرت توضيحي لآلية التحديث التلقائي الفضائية
        Surface(
            color = Color.White.copy(alpha = 0.04f),
            shape = SpatialInsetShape,
            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "كيف يعمل التحديث التلقائي؟",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF00E5FF)
                    )
                }
                Text(
                    text = UpdateChannelFacade.behaviorText(context),
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SpatialTabButton(
    label: String,
    count: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = SpatialButtonShape,
        color = if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, activeColor) else null,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) activeColor else TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontFamily = CairoFont,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
                color = if (isSelected) Color.White else TextSecondary,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                color = if (isSelected) activeColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                shape = CircleShape
            ) {
                Text(
                    text = count,
                    fontFamily = RobotoMonoFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) activeColor else TextSecondary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * بطاقة نموذج GGUF مع شريط التقدم الحي والمعلومات الكاملة
 */
@Composable
private fun GgufModelCard(
    model: OnDeviceModelInfo,
    isInstalled: Boolean,
    isSelected: Boolean,
    installedSizeBytes: Long,
    downloadState: ModelDownloadState,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSelectAsDefault: () -> Unit,
    onDeleteClick: () -> Unit,
    onTestPromptClick: () -> Unit
) {
    val isDownloadingThis = downloadState is ModelDownloadState.Downloading && downloadState.modelId == model.id
    val isInterruptedThis = downloadState is ModelDownloadState.Interrupted && downloadState.modelId == model.id

    val borderColor = when {
        isDownloadingThis -> Color(0xFF00E5FF)
        isSelected -> GoldPrimary
        isInstalled -> Color(0xFF10B981)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Surface(
        color = CardSurface.copy(alpha = 0.9f),
        shape = SpatialCardShape,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // الصف العلوي: الاسم والبادج والحجم
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = model.name,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StrictBadge(
                            text = "GGUF",
                            color = Color(model.badgeColorHex)
                        )
                        if (model.isRecommended) {
                            StrictBadge(
                                text = "موصى به ⭐",
                                color = GoldPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = model.tag,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = Color(model.badgeColorHex)
                        )
                        Text(
                            text = "• ${model.ramRequirement}",
                            fontFamily = RobotoMonoFont,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                // الحجم التخزيني
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = SpatialInsetShape,
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = model.sizeFormatted,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = GoldPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // وصف النموذج
            Text(
                text = model.description,
                fontFamily = CairoFont,
                fontSize = 11.5.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            // حالة التنزيل الجارية (شريط التقدم الحي)
            if (isDownloadingThis) {
                val state = downloadState as ModelDownloadState.Downloading
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "جارٍ التنزيل: ${(state.progress * 100).toInt()}%",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${StorageMetrics.formatBytes(state.downloadedBytes)} من ${StorageMetrics.formatBytes(state.totalBytes)} • ${state.speedKbps} ك.ب/ث",
                            fontFamily = RobotoMonoFont,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CutCornerShape(2.dp)),
                        color = Color(0xFF00E5FF),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onCancelClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إلغاء التنزيل", fontFamily = CairoFont, color = Color(0xFFEF4444), fontSize = 11.sp)
                        }
                    }
                }
            }

            // صف الإجراءات والأزرار
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isInstalled) {
                    // مثبت وجاهز
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSelected) "النموذج النشط حالياً ✅" else "جاهز للاستخدام بدون إنترنت",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // زر تجربة استجابة النموذج
                        IconButton(
                            onClick = onTestPromptClick,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "تجربة", tint = Color(0xFF00E5FF), modifier = Modifier.size(17.dp))
                        }

                        if (!isSelected) {
                            OutlinedButton(
                                onClick = onSelectAsDefault,
                                shape = SpatialInsetShape,
                                border = BorderStroke(0.8.dp, GoldPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("تعيين كنشط", fontFamily = CairoFont, fontSize = 11.sp, color = GoldPrimary)
                            }
                        }

                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(17.dp))
                        }
                    }
                } else if (!isDownloadingThis) {
                    // غير مثبت: زر التنزيل
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "متاح للتنزيل المباشر من HuggingFace",
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onDownloadClick,
                        shape = SpatialInsetShape,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تنزيل (${model.sizeFormatted})", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = DeepSlate)
                    }
                }
            }
        }
    }
}

/**
 * شريط رأس تبويب الريلز
 */
@Composable
private fun ReelsDownloadsHeader(
    onOpenQuickDownload: () -> Unit,
    onBrowseReels: () -> Unit
) {
    Surface(
        color = CardSurface.copy(alpha = 0.85f),
        shape = SpatialCardShape,
        border = BorderStroke(0.8.dp, Color(0xFFA855F7).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SlowMotionVideo,
                    contentDescription = null,
                    tint = Color(0xFFA855F7),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "فيديوهات الريلز المنزلة",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "مشاهدة الريلز بجودة عالية أوفلاين بدون استهلاك بيانات",
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenQuickDownload,
                    shape = SpatialButtonShape,
                    border = BorderStroke(0.8.dp, Color(0xFFA855F7)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("رابط", fontFamily = CairoFont, fontSize = 10.5.sp, color = Color(0xFFA855F7), maxLines = 1)
                }

                Button(
                    onClick = onBrowseReels,
                    shape = SpatialButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Text(
                        "استعراض 🎬",
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * بطاقة ريل قيد التنزيل حالياً
 */
@Composable
private fun ActiveReelDownloadCard(
    progress: ReelDownloadProgress,
    onCancel: () -> Unit
) {
    Surface(
        color = CardSurface.copy(alpha = 0.95f),
        shape = SpatialCardShape,
        border = BorderStroke(1.dp, Color(0xFFA855F7)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // عدّاد تنزيل الريلز — موحّد عبر TransferCounterBar
            TransferCounterBar(
                label = "تنزيل: ${progress.title}",
                sentBytes = progress.downloadedBytes,
                totalBytes = progress.totalBytes,
                isUpload = false,
                speedText = if (progress.speedKbps > 0) "${progress.speedKbps} ك.ب/ث" else null
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) {
                    Text("إلغاء التنزيل", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * بطاقة ريل منزّل ومحفوظ في الذاكرة
 */
@Composable
private fun DownloadedReelCard(
    reel: DownloadedReelItem,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = CardSurface.copy(alpha = 0.85f),
        shape = SpatialCardShape,
        border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // أيقونة الفيديو
                Surface(
                    color = Color(0xFFA855F7).copy(alpha = 0.15f),
                    shape = SpatialButtonShape,
                    border = BorderStroke(0.8.dp, Color(0xFFA855F7).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onPlay() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "تشغيل",
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reel.title,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = reel.author,
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "• ${reel.formattedSize}",
                            fontFamily = RobotoMonoFont,
                            fontSize = 10.sp,
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        StrictBadge(
                            text = "ريلز",
                            color = Color(0xFFA855F7)
                        )
                    }
                }
            }

            // أزرار التحكم في صف مستقل — لا تزاحم النص
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = "تشغيل", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(17.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * بطاقة ملف وسائط أو صوتي منزّل
 */
@Composable
private fun DownloadedMediaCard(
    media: DownloadedMediaItem,
    onDelete: () -> Unit
) {
    Surface(
        color = CardSurface.copy(alpha = 0.85f),
        shape = SpatialInsetShape,
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.title,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${media.subtitle} • ${media.formattedSize}",
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        StrictBadge(
                            text = "صوتيات",
                            color = GoldPrimary
                        )
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(17.dp))
            }
        }
    }
}

/**
 * واجهة الحالة الفارغة
 */
@Composable
private fun EmptyDownloadsPlaceholder(
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit
) {
    Surface(
        color = CardSurface.copy(alpha = 0.5f),
        shape = SpatialCardShape,
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = GoldPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = title,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                fontFamily = CairoFont,
                fontSize = 11.5.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Button(
                onClick = onAction,
                shape = SpatialInsetShape,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = actionText,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = DeepSlate
                )
            }
        }
    }
}

/**
 * نافذة اختبار الاستدلال المحلي السريع
 */
@Composable
private fun LocalInferenceTestDialog(
    model: OnDeviceModelInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var promptInput by remember { mutableStateOf("اكتب دالة بلغة Kotlin لحساب مجموع أرقام في مصفوفة") }
    var isGenerating by remember { mutableStateOf(false) }
    var outputText by remember { mutableStateOf("") }
    var latencyMs by remember { mutableLongStateOf(0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF00E5FF))
                Text(
                    text = "اختبار ${model.name} (GGUF)",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "تنفيذ محلي على المعالج بدون إنترنت عبر ملف GGUF:",
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    label = { Text("الأمر أو السؤال البرمجي", fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00E5FF))
                        Text("جارٍ المعالجة في الذاكرة...", fontFamily = CairoFont, fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }
                }

                if (outputText.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = SpatialInsetShape,
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("الاستجابة المولدة:", fontFamily = CairoFont, fontSize = 10.sp, color = GoldPrimary)
                                Text("${latencyMs}ms", fontFamily = RobotoMonoFont, fontSize = 9.sp, color = Color(0xFF10B981))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = outputText,
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (promptInput.isBlank()) return@Button
                    isGenerating = true
                    outputText = ""
                    scope.launch {
                        val res = LocalAgentInferenceEngine.executeLocalCodeSynthesis(
                            context = context,
                            userPrompt = promptInput,
                            specialization = AgentSpecialization.TURBO
                        )
                        isGenerating = false
                        res.onSuccess {
                            outputText = it.outputText
                            latencyMs = it.executionLatencyMs
                        }.onFailure { err ->
                            outputText = "خطأ في التنفيذ: ${err.message}"
                        }
                    }
                },
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("توليد محلي ⚡", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = DeepSlate)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", fontFamily = CairoFont, color = TextSecondary)
            }
        },
        containerColor = CardSurface
    )
}

/**
 * تشغيل فيديو محلي عبر مشغل النظام
 */
private fun playVideoLocally(context: Context, localPath: String) {
    runCatching {
        val file = File(localPath)
        if (!file.exists()) {
            Toast.makeText(context, "الملف غير موجود في الذاكرة", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "تعذر فتح مشغل الفيديو الخارجي", Toast.LENGTH_SHORT).show()
    }
}

/**
 * مشاركة ملف الفيديو عبر التطبيقات
 */
private fun shareVideoFile(context: Context, localPath: String, title: String) {
    runCatching {
        val file = File(localPath)
        if (!file.exists()) {
            Toast.makeText(context, "الملف غير موجود", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "شاهد ريل «$title» عبر استوديو قبس")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفيديو"))
    }.onFailure {
        Toast.makeText(context, "فشلت مشاركة الفيديو", Toast.LENGTH_SHORT).show()
    }
}
