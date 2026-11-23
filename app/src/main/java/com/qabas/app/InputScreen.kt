package com.qabas.app

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CutCornerShape
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.HudCornerDecor
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(
    state: ProjectState,
    onStateChange: (ProjectState) -> Unit,
    onProceed: () -> Unit,
    onBack: () -> Unit = {},
    onOpenAudioLibrary: () -> Unit = {},
    onOpenDirector: () -> Unit = {},
    onOpenQasas: () -> Unit = {},
    onOpenClips: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedSourceType by remember {
        mutableStateOf<String?>(if (state.inputText.isNotBlank()) "idea" else null)
    }

    var trendingIdeas by remember { mutableStateOf<List<TrendingIdea>?>(null) }
    var isTrendingLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current
    var savedUserStyles by remember { mutableStateOf<List<ClonedStylePreset>>(emptyList()) }
    var audioUsageMode by remember { mutableStateOf("voiceover") } // "voiceover" or "primary"

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "مقطع_صوتي.mp3"
            var fileSizeFormatted: String? = null
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        val size = cursor.getLong(sizeIndex)
                        if (size > 0) {
                            val sizeMb = size / (1024.0 * 1024.0)
                            fileSizeFormatted = if (sizeMb >= 1.0) {
                                String.format(java.util.Locale.US, "%.1f ميغابايت", sizeMb)
                            } else {
                                val sizeKb = size / 1024.0
                                String.format(java.util.Locale.US, "%.0f كيلوبايت", sizeKb)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            var durationFormatted: String? = null
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                retriever.release()
                if (durationMs != null && durationMs > 0) {
                    val totalSeconds = durationMs / 1000
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    durationFormatted = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                }
            } catch (_: Exception) {}

            val metaParts = listOfNotNull(durationFormatted, fileSizeFormatted).joinToString(" • ")
            val label = if (metaParts.isNotBlank()) "$fileName ($metaParts)" else fileName

            onStateChange(
                state.copy(
                    sourceType = "audio",
                    sourceUris = listOf(uri.toString()),
                    sourceLabel = label,
                    projectTitle = if (state.projectTitle == "مشروع جديد" || state.projectTitle.isBlank()) "صوت: ${fileName.take(20)}" else state.projectTitle
                )
            )
            scope.launch {
                snackbarHostState.showSnackbar(Translator.tr("تم اختيار الملف الصوتي بنجاح: ") + fileName)
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(Translator.tr("تم إلغاء اختيار الملف الصوتي"))
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "فيديو_مستورد.mp4"
            var formattedDuration: String? = null
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                }
            } catch (_: Exception) {}

            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                retriever.release()
                if (durationMs != null && durationMs > 0) {
                    val totalSeconds = durationMs / 1000
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    formattedDuration = String.format("%02d:%02d", minutes, seconds)
                }
            } catch (_: Exception) {}

            val label = if (formattedDuration != null) "$fileName ($formattedDuration)" else fileName
            onStateChange(
                state.copy(
                    sourceType = "video",
                    sourceUris = listOf(uri.toString()),
                    sourceLabel = label,
                    projectTitle = if (state.projectTitle == "مشروع جديد" || state.projectTitle.isBlank()) "فيديو: ${fileName.take(20)}" else state.projectTitle
                )
            )
            scope.launch {
                snackbarHostState.showSnackbar(Translator.tr("تم اختيار الفيديو بنجاح: ") + fileName)
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(Translator.tr("تم إلغاء اختيار الفيديو"))
            }
        }
    }

    val imagesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val currentUris = if (state.sourceType == "images") state.sourceUris else emptyList()
            val mergedUris = (currentUris + uris.map { it.toString() }).distinct()
            val label = "${mergedUris.size} صور مختارة"
            onStateChange(
                state.copy(
                    sourceType = "images",
                    sourceUris = mergedUris,
                    sourceLabel = label,
                    projectTitle = if (state.projectTitle == "مشروع جديد" || state.projectTitle.isBlank()) "ألبوم صور (${mergedUris.size})" else state.projectTitle
                )
            )
            scope.launch {
                snackbarHostState.showSnackbar(Translator.tr("تم اختيار ${uris.size} صور بنجاح ✓"))
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(Translator.tr("تم إلغاء اختيار الصور"))
            }
        }
    }

    LaunchedEffect(Unit) {
        savedUserStyles = StyleVaultManager.getVaultStyles(context)
    }

    // Direct Style Analysis State

    // Voice Input State
    var isRecordingVoice by remember { mutableStateOf(false) }
    var isEnhancingIdea by remember { mutableStateOf(false) }

    // Content Guard Pre-flight Validation State
    var guardInspectionResult by remember { mutableStateOf<ContentInspectionResult?>(null) }
    var showGuardDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    LaunchedEffect(Unit) {
        try {
            isTrendingLoading = true
            trendingIdeas = AppServices.getTrendingIdeas()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            trendingIdeas = null
        } finally {
            isTrendingLoading = false
        }
    }

    val goldGradient = Brush.horizontalGradient(colors = listOf(PresetAccent, PresetPrimary))

    Box(modifier = Modifier.fillMaxSize()) {
        // Spatial HUD Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val primaryColor = PresetPrimary
            val stepX = 50.dp.toPx()
            val stepY = 50.dp.toPx()
            val w = size.width
            val h = size.height

            for (i in 0..(w / stepX).toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.05f),
                    start = Offset(i * stepX, 0f),
                    end = Offset(i * stepX, h),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            for (j in 0..(h / stepY).toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.05f),
                    start = Offset(0f, j * stepY),
                    end = Offset(w, j * stepY),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Translator.tr("صناعة الفكرة والإخراج 🎬"),
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            Translator.tr("استوديو خادم المسلم الذكي لإنتاج الفيديوهات"),
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(QabasTheme.current.cardSurface, CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Translator.tr("العودة"),
                            tint = PresetPrimary
                        )
                    }
                },
                actions = {
                    val streak = remember { try { StreakManager.getStreak(context) } catch (_: Exception) { 0 } }
                    if (streak > 0) {
                        Surface(shape = CutCornerShape(8.dp), color = QabasTheme.current.warning.copy(alpha = 0.15f), modifier = Modifier.padding(end = 12.dp)) {
                            Text("🔥 $streak", color = QabasTheme.current.warning, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                selectedSourceType == null -> {
                    DefaultSourceSelectionView(
                        onSelectType = { type ->
                            selectedSourceType = type
                            onStateChange(state.copy(sourceType = type))
                        },
                        onOpenDirector = onOpenDirector,
                        onOpenQasas = onOpenQasas,
                        onOpenClips = onOpenClips,
                        onPickTrending = { t ->
                            selectedSourceType = "idea"
                            onStateChange(state.copy(sourceType = "idea", inputText = t))
                        }
                    )
                }
                selectedSourceType == "video" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top Indicator / Switcher Badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(QabasTheme.current.cardSurface, CutCornerShape(8.dp))
                                .border(1.dp, QabasTheme.current.success.copy(alpha = 0.3f), CutCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = QabasTheme.current.success, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    Translator.tr("المدخل المختار: فيديو من الجهاز 🎬"),
                                    color = QabasTheme.current.success,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    selectedSourceType = null
                                    onStateChange(state.copy(sourceType = "idea"))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    Translator.tr("تغيير ↩️"),
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Header Card
                        GlassmorphicSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CutCornerShape(12.dp),
                            backgroundColor = QabasTheme.current.cardSurface.copy(alpha = 0.85f),
                            borderColor = QabasTheme.current.success.copy(alpha = 0.4f)
                        ) {
                            HudCornerDecor(modifier = Modifier.matchParentSize(), color = QabasTheme.current.success.copy(alpha = 0.1f))
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = QabasTheme.current.success.copy(alpha = 0.15f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = QabasTheme.current.success, modifier = Modifier.size(26.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        Translator.tr("استيراد فيديو من الجهاز 🎬"),
                                        color = QabasTheme.current.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        Translator.tr("إعادة المونتاج، تفريغ الصوت، وتطبيق قوالب خادم المسلم"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (state.sourceUris.isEmpty()) {
                            // Picker Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                shape = CutCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = QabasTheme.current.background),
                                border = BorderStroke(1.5.dp, QabasTheme.current.success.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = QabasTheme.current.success.copy(alpha = 0.18f),
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = QabasTheme.current.success, modifier = Modifier.size(34.dp))
                                        }
                                    }
                                    Text(
                                        Translator.tr("اضغط لاختيار فيديو من المعرض"),
                                        color = QabasTheme.current.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        Translator.tr("يدعم صيغ MP4 و MOV بدقة عالية"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = {
                                            videoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.success),
                                        shape = CutCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        Icon(Icons.Default.VideoCall, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            Translator.tr("فتح معرض الفيديوهات 📂"),
                                            color = DeepSlate,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            // Preview Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = CutCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = QabasTheme.current.cardSurface),
                                border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CutCornerShape(10.dp),
                                            color = QabasTheme.current.success.copy(alpha = 0.15f),
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Movie, contentDescription = null, tint = QabasTheme.current.success, modifier = Modifier.size(28.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = state.sourceLabel ?: Translator.tr("فيديو مختار"),
                                                color = QabasTheme.current.textPrimary,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 2
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = CutCornerShape(4.dp),
                                                color = QabasTheme.current.success.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = Translator.tr("جاهز للمونتاج والتعديل ✓"),
                                                    color = QabasTheme.current.success,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Action buttons row (Change / Remove)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                videoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                )
                                            },
                                            shape = CutCornerShape(8.dp),
                                            border = BorderStroke(1.dp, QabasTheme.current.separator),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(Translator.tr("تغيير الفيديو"), color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onStateChange(
                                                    state.copy(
                                                        sourceUris = emptyList(),
                                                        sourceLabel = null
                                                    )
                                                )
                                            },
                                            shape = CutCornerShape(8.dp),
                                            border = BorderStroke(1.dp, QabasTheme.current.destructive.copy(alpha = 0.5f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = QabasTheme.current.destructive, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(Translator.tr("إزالة 🗑️"), color = QabasTheme.current.destructive, fontFamily = NotoSansFont, fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Big Proceed Button
                                    Button(
                                        onClick = {
                                            val desc = state.inputText.ifBlank { "مشروع فيديو مستورد: ${state.sourceLabel ?: "مقطع مرئي"}" }
                                            onStateChange(
                                                state.copy(
                                                    sourceType = "video",
                                                    inputText = desc
                                                )
                                            )
                                            onProceed()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                                        shape = CutCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Text(
                                            Translator.tr("متابعة إلى المونتاج والتعديل 🚀"),
                                            color = DeepSlate,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        // Back Button
                        TextButton(
                            onClick = {
                                selectedSourceType = null
                                onStateChange(state.copy(sourceType = "idea"))
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Translator.tr("تغيير نوع المدخل والعودة"), color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp)
                        }
                    }
                }
                selectedSourceType == "audio" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top Indicator / Switcher Badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(QabasTheme.current.cardSurface, CutCornerShape(8.dp))
                                .border(1.dp, QabasTheme.current.primary.copy(alpha = 0.3f), CutCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = QabasTheme.current.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    Translator.tr("المدخل المختار: ملف صوتي 🎙️"),
                                    color = QabasTheme.current.primary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    selectedSourceType = null
                                    onStateChange(state.copy(sourceType = "idea"))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    Translator.tr("تغيير ↩️"),
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Header Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CutCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = QabasTheme.current.cardSurface),
                            border = BorderStroke(1.dp, QabasTheme.current.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = QabasTheme.current.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = QabasTheme.current.primary, modifier = Modifier.size(26.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        Translator.tr("استيراد ملف صوتي 🎙️"),
                                        color = QabasTheme.current.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        Translator.tr("تفريغ، تعليق صوتي، أو مسار صوتي أساسي للمشروع"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (state.sourceUris.isEmpty()) {
                            // Picker Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        audioPickerLauncher.launch("audio/*")
                                    },
                                shape = CutCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = QabasTheme.current.background),
                                border = BorderStroke(1.5.dp, QabasTheme.current.primary.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = QabasTheme.current.primary.copy(alpha = 0.18f),
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = QabasTheme.current.primary, modifier = Modifier.size(34.dp))
                                        }
                                    }
                                    Text(
                                        Translator.tr("اضغط لاختيار ملف صوتي من الجهاز"),
                                        color = QabasTheme.current.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        Translator.tr("يدعم صيغ MP3 و WAV و M4A و AAC و OGG"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                audioPickerLauncher.launch("audio/*")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary),
                                            shape = CutCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.AudioFile, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                Translator.tr("من الجهاز 📂"),
                                                color = DeepSlate,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = onOpenAudioLibrary,
                                            border = BorderStroke(1.dp, PresetPrimary),
                                            shape = CutCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                Translator.tr("مكتبة خادم المسلم 🎙️"),
                                                color = PresetPrimary,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // File Info & Usage Choice Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = CutCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = QabasTheme.current.cardSurface),
                                border = BorderStroke(1.dp, QabasTheme.current.primary.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Info Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CutCornerShape(10.dp),
                                            color = QabasTheme.current.primary.copy(alpha = 0.15f),
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = QabasTheme.current.primary, modifier = Modifier.size(28.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = state.sourceLabel ?: Translator.tr("ملف صوتي مختار"),
                                                color = QabasTheme.current.textPrimary,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 2
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = CutCornerShape(4.dp),
                                                color = QabasTheme.current.primary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = Translator.tr("ملف صوتي مستورد ✓"),
                                                    color = QabasTheme.current.primary,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = QabasTheme.current.separator)

                                    // Usage Mode Choice
                                    Text(
                                        Translator.tr("اختر كيفية توظيف هذا الصوت:"),
                                        color = PresetPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )

                                    // Option A: Voiceover
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { audioUsageMode = "voiceover" },
                                        shape = CutCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (audioUsageMode == "voiceover") QabasTheme.current.secondarySurface else QabasTheme.current.secondarySurface
                                        ),
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (audioUsageMode == "voiceover") QabasTheme.current.primary else QabasTheme.current.separator
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = audioUsageMode == "voiceover",
                                                onClick = { audioUsageMode = "voiceover" },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = QabasTheme.current.primary,
                                                    unselectedColor = TextSecondary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    Translator.tr("استخدام كتعليق صوتي (Voiceover) 🎙️"),
                                                    color = QabasTheme.current.textPrimary,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    Translator.tr("اعتماد هذا الصوت كإلقاء صوتي أساسي وتوليد مشاهد ملائمة لكلماته"),
                                                    color = TextSecondary,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 11.sp,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }

                                    // Option B: Primary Material
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { audioUsageMode = "primary" },
                                        shape = CutCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (audioUsageMode == "primary") QabasTheme.current.secondarySurface else QabasTheme.current.secondarySurface
                                        ),
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (audioUsageMode == "primary") QabasTheme.current.primary else QabasTheme.current.separator
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = audioUsageMode == "primary",
                                                onClick = { audioUsageMode = "primary" },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = QabasTheme.current.primary,
                                                    unselectedColor = TextSecondary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    Translator.tr("استخدامه كمادة أساسية للمشروع 🎵"),
                                                    color = QabasTheme.current.textPrimary,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    Translator.tr("اعتماد الصوت كمسار أساسي للمشروع مع بناء المشاهد والسكريبت وفقاً له"),
                                                    color = TextSecondary,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 11.sp,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Action buttons row (Change / Remove)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                audioPickerLauncher.launch("audio/*")
                                            },
                                            shape = CutCornerShape(8.dp),
                                            border = BorderStroke(1.dp, QabasTheme.current.separator),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(Translator.tr("تغيير الملف"), color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onStateChange(
                                                    state.copy(
                                                        sourceUris = emptyList(),
                                                        sourceLabel = null
                                                    )
                                                )
                                            },
                                            shape = CutCornerShape(8.dp),
                                            border = BorderStroke(1.dp, QabasTheme.current.destructive.copy(alpha = 0.5f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = QabasTheme.current.destructive, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(Translator.tr("إزالة 🗑️"), color = QabasTheme.current.destructive, fontFamily = NotoSansFont, fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Big Proceed Button
                                    Button(
                                        onClick = {
                                            val cleanName = state.sourceLabel?.substringBefore(" (") ?: "ملف صوتي"
                                            val desc = if (audioUsageMode == "voiceover") {
                                                state.inputText.ifBlank { "مشروع بتعليق صوتي: $cleanName" }
                                            } else {
                                                state.inputText.ifBlank { "مشروع صوتي أساسي: $cleanName" }
                                            }
                                            onStateChange(
                                                state.copy(
                                                    sourceType = "audio",
                                                    inputText = desc,
                                                    voiceOver = if (audioUsageMode == "voiceover") "صوت مخصص: $cleanName" else state.voiceOver
                                                )
                                            )
                                            onProceed()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                                        shape = CutCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Text(
                                            Translator.tr("متابعة إلى المسار الذكي 🚀"),
                                            color = DeepSlate,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        // Back Button
                        TextButton(
                            onClick = {
                                selectedSourceType = null
                                onStateChange(state.copy(sourceType = "idea"))
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Translator.tr("تغيير نوع المدخل والعودة"), color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp)
                        }
                    }
                }
                selectedSourceType == "images" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top Indicator / Switcher Badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(QabasTheme.current.cardSurface, CutCornerShape(8.dp))
                                .border(1.dp, QabasTheme.current.warning.copy(alpha = 0.3f), CutCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = QabasTheme.current.warning, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    Translator.tr("المدخل المختار: ألبوم صور 🖼️"),
                                    color = QabasTheme.current.warning,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    selectedSourceType = null
                                    onStateChange(state.copy(sourceType = "idea"))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    Translator.tr("تغيير ↩️"),
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Header Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CutCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = QabasTheme.current.cardSurface),
                            border = BorderStroke(1.dp, QabasTheme.current.warning.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = QabasTheme.current.warning.copy(alpha = 0.15f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = QabasTheme.current.warning, modifier = Modifier.size(26.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        Translator.tr("استيراد ألبوم صور 🖼️"),
                                        color = QabasTheme.current.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        Translator.tr("تحويل الصور والتصاميم إلى ريلز متحرك مع مؤثرات بصرية"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (state.sourceUris.isEmpty()) {
                            // Picker Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        imagesPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                shape = CutCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = QabasTheme.current.background),
                                border = BorderStroke(1.5.dp, QabasTheme.current.warning.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = QabasTheme.current.warning.copy(alpha = 0.18f),
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = QabasTheme.current.warning, modifier = Modifier.size(34.dp))
                                        }
                                    }
                                    Text(
                                        Translator.tr("اضغط لاختيار صور من المعرض"),
                                        color = QabasTheme.current.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        Translator.tr("يمكنك اختيار حتى 15 صورة (JPEG, PNG, WEBP) لتوليد المشاهد"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = {
                                            imagesPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.warning),
                                        shape = CutCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(Translator.tr("فتح المعرض واختيار الصور"), color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            // Thumbnails Preview & Configuration Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = CutCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = QabasTheme.current.cardSurface),
                                border = BorderStroke(1.dp, QabasTheme.current.warning.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Status Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = QabasTheme.current.warning.copy(alpha = 0.2f),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Collections, contentDescription = null, tint = QabasTheme.current.warning, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "${state.sourceUris.size} " + Translator.tr("صور مختارة"),
                                                    color = QabasTheme.current.textPrimary,
                                                    fontFamily = CairoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    text = Translator.tr("جاهزة للتحويل إلى مشاهد ريلز ✓"),
                                                    color = QabasTheme.current.success,
                                                    fontFamily = NotoSansFont,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                imagesPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier
                                                .background(QabasTheme.current.separator, CircleShape)
                                                .size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = Translator.tr("إضافة صور"), tint = QabasTheme.current.warning, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    // Image Thumbnails Carousel
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(state.sourceUris) { index, uriString ->
                                            Box(
                                                modifier = Modifier
                                                    .size(88.dp)
                                                    .clip(CutCornerShape(8.dp))
                                                    .border(1.dp, QabasTheme.current.separator, CutCornerShape(8.dp))
                                            ) {
                                                AsyncImage(
                                                    model = uriString,
                                                    contentDescription = "صورة ${index + 1}",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )

                                                // Badge number
                                                Surface(
                                                    shape = CutCornerShape(bottomEnd = 8.dp),
                                                    color = Color.Black.copy(alpha = 0.7f),
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                ) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        color = QabasTheme.current.textPrimary,
                                                        fontSize = 10.sp,
                                                        fontFamily = NotoSansFont,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                // Delete button
                                                IconButton(
                                                    onClick = {
                                                        val updated = state.sourceUris.filterIndexed { i, _ -> i != index }
                                                        onStateChange(
                                                            state.copy(
                                                                sourceUris = updated,
                                                                sourceLabel = if (updated.isNotEmpty()) "${updated.size} صور مختارة" else null
                                                            )
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .align(Alignment.TopEnd)
                                                        .padding(2.dp)
                                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = Translator.tr("حذف"), tint = QabasTheme.current.destructive, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }

                                        item {
                                            // Add More Box in Carousel
                                            Box(
                                                modifier = Modifier
                                                    .size(88.dp)
                                                    .clip(CutCornerShape(8.dp))
                                                    .border(1.dp, QabasTheme.current.warning.copy(alpha = 0.4f), CutCornerShape(8.dp))
                                                    .background(QabasTheme.current.background)
                                                    .clickable {
                                                        imagesPickerLauncher.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = QabasTheme.current.warning, modifier = Modifier.size(24.dp))
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(Translator.tr("إضافة"), color = TextSecondary, fontSize = 10.sp, fontFamily = CairoFont)
                                                }
                                            }
                                        }
                                    }

                                    // Script / Topic hint input
                                    OutlinedTextField(
                                        value = state.inputText,
                                        onValueChange = { onStateChange(state.copy(inputText = it)) },
                                        placeholder = {
                                            Text(
                                                Translator.tr("اكتب فكرة أو موضوع الريلز (اختياري)..."),
                                                color = TextSecondary.copy(alpha = 0.6f),
                                                fontSize = 13.sp,
                                                fontFamily = NotoSansFont
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = CutCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = QabasTheme.current.warning,
                                            unfocusedBorderColor = QabasTheme.current.separator,
                                            focusedContainerColor = QabasTheme.current.background,
                                            unfocusedContainerColor = QabasTheme.current.background,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        maxLines = 2
                                    )

                                    // Action buttons row (Add More / Clear)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                imagesPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            shape = CutCornerShape(8.dp),
                                            border = BorderStroke(1.dp, QabasTheme.current.separator),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(Translator.tr("إضافة صور"), color = TextPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onStateChange(
                                                    state.copy(
                                                        sourceUris = emptyList(),
                                                        sourceLabel = null
                                                    )
                                                )
                                            },
                                            shape = CutCornerShape(8.dp),
                                            border = BorderStroke(1.dp, QabasTheme.current.destructive.copy(alpha = 0.5f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = QabasTheme.current.destructive, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(Translator.tr("مسح الكل 🗑️"), color = QabasTheme.current.destructive, fontFamily = NotoSansFont, fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Big Proceed Button
                                    Button(
                                        onClick = {
                                            val desc = state.inputText.ifBlank { "مشروع ريلز متحرك من ${state.sourceUris.size} صور" }
                                            onStateChange(
                                                state.copy(
                                                    sourceType = "images",
                                                    inputText = desc
                                                )
                                            )
                                            onProceed()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                                        shape = CutCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Text(
                                            Translator.tr("متابعة إلى المسار الذكي 🚀"),
                                            color = DeepSlate,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        // Back Button
                        TextButton(
                            onClick = {
                                selectedSourceType = null
                                onStateChange(state.copy(sourceType = "idea"))
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Translator.tr("تغيير نوع المدخل والعودة"), color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp)
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Source Type Indicator / Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(QabasTheme.current.cardSurface, CutCornerShape(8.dp))
                                .border(1.dp, QabasTheme.current.separator, CutCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    Translator.tr("المدخل المختار: فكرة نصية ✍️"),
                                    color = PresetPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            TextButton(
                                onClick = { selectedSourceType = null },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    Translator.tr("تغيير ↩️"),
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // 1. CORE PROMPT & IDEA FIELD CARD
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(12.dp, CutCornerShape(12.dp), spotColor = PresetPrimary.copy(alpha = 0.25f)),
                            shape = CutCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = QabasTheme.current.secondarySurface),
                            border = BorderStroke(
                                1.5.dp,
                                if (state.inputText.isNotEmpty()) PresetPrimary else QabasTheme.current.separator
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            Translator.tr("اكتب فكرة المشهد أو النص ✍️"),
                                            color = PresetPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    if (state.inputText.isNotEmpty()) {
                                        TextButton(onClick = { onStateChange(state.copy(inputText = "")) }) {
                                            Text(Translator.tr("مسح"), color = QabasTheme.current.destructive, fontSize = 12.sp, fontFamily = NotoSansFont)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = state.inputText,
                                    onValueChange = { newVal -> onStateChange(state.copy(inputText = newVal)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 130.dp, max = 220.dp),
                                    placeholder = {
                                        Text(
                                            Translator.tr("مثال: شرح مبسط وعميق لفضل تدبر القرآن الكريم في حسم القرارات، بأسلوب سينمائي حماسي مع مشاهد طبيعية هادئة..."),
                                            color = TextSecondary.copy(alpha = 0.6f),
                                            fontFamily = NotoSansFont,
                                            fontSize = 13.sp,
                                            lineHeight = 22.sp
                                        )
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = QabasTheme.current.background,
                                        unfocusedContainerColor = QabasTheme.current.background,
                                        focusedIndicatorColor = PresetPrimary,
                                        unfocusedIndicatorColor = QabasTheme.current.separator,
                                        focusedTextColor = QabasTheme.current.textPrimary,
                                        unfocusedTextColor = QabasTheme.current.textPrimary,
                                        cursorColor = PresetPrimary
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 15.sp,
                                        fontFamily = NotoSansFont,
                                        lineHeight = 24.sp
                                    ),
                                    shape = CutCornerShape(10.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Quick Assist Toolbar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { isRecordingVoice = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary.copy(alpha = 0.15f), contentColor = PresetPrimary),
                                        shape = CutCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(Translator.tr("صوت 🎤"), fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val holySnippet = "« إِنَّ اللَّهَ وَمَلَائِكَتَهُ يُصَلُّونَ عَلَى النَّبِيِّ ۚ يَا أَيُّهَا الَّذِينَ آمَنُوا صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا »"
                                            onStateChange(
                                                state.copy(
                                                    inputText = if (state.inputText.isBlank()) holySnippet else "${state.inputText}\n$holySnippet"
                                                )
                                            )
                                        },
                                        border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.5f)),
                                        shape = CutCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(Translator.tr("نص شرعي 📖"), color = PresetPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Magic Prompt Engineer Button
                                Button(
                                    onClick = {
                                        if (state.inputText.isNotBlank() && !isEnhancingIdea) {
                                            isEnhancingIdea = true
                                            scope.launch {
                                                kotlinx.coroutines.delay(1500)
                                                val enhanced = """
                                                    |[الهدف]: إنتاج فيديو دعوي/إسلامي قصير احترافي.
                                                    |[الجمهور المستهدف]: الشباب المسلم على شبكات التواصل.
                                                    |[النبرة]: ملهمة، سينمائية، ومؤثرة.
                                                    |[الفكرة الأساسية]: ${state.inputText.take(150)}${if (state.inputText.length > 150) "..." else ""}
                                                    |
                                                    |[الهيكلة المقترحة]:
                                                    |1. الخطاف (الثواني 0-3): مشهد يشد الانتباه مع سؤال مثير للتفكير.
                                                    |2. الجسد (القصة/المعنى): طرح القضية مع الاستشهاد بآية أو حديث بخلفية هادئة.
                                                    |3. الخاتمة (الدعوة للإجراء CTA): رسالة ختامية تترك أثراً مع دعوة للمشاركة.
                                                    |
                                                    |[التوجيهات المرئية]: ألوان داكنة مع تباين ذهبي، انتقالات ناعمة، ونصوص واضحة.
                                                """.trimMargin()
                                                
                                                onStateChange(state.copy(inputText = enhanced))
                                                isEnhancingIdea = false
                                                snackbarHostState.showSnackbar(Translator.tr("تمت هندسة الفكرة لتصبح برومبت احترافي! ✨"))
                                            }
                                        } else if (state.inputText.isBlank()) {
                                            scope.launch { snackbarHostState.showSnackbar(Translator.tr("يرجى كتابة الفكرة أولاً!")) }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.primary),
                                    shape = CutCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    enabled = !isEnhancingIdea
                                ) {
                                    if (isEnhancingIdea) {
                                        CircularProgressIndicator(color = QabasTheme.current.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(Translator.tr("جاري هندسة البرومبت ذكياً..."), color = QabasTheme.current.onPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = QabasTheme.current.onPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(Translator.tr("الصياغة الهندسية الذكية للبرومبت 🪄"), color = QabasTheme.current.onPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        // 2. VIDEO PARAMETERS CARD (المدة، النسبة، الجمهور المستهدف، النبرة)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CutCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = QabasTheme.current.cardSurface),
                            border = BorderStroke(1.dp, QabasTheme.current.separator)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Header: Parameters
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        Translator.tr("إعدادات الفيديو الأساسية ⚙️"),
                                        color = PresetPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                // 1. Aspect Ratio (الأبعاد: طول / عرض)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        Translator.tr("أبعاد الفيديو (الارتفاع والعرض):"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "9:16" to "طولي (رأسي)",
                                            "16:9" to "عرضي (YouTube)",
                                            "1:1" to "مربع (Feed)"
                                        ).forEach { (ratio, label) ->
                                            val isSelected = state.selectedRatio == ratio
                                            Surface(
                                                color = if (isSelected) PresetPrimary else QabasTheme.current.background,
                                                shape = CutCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (isSelected) PresetPrimary else QabasTheme.current.separator),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { onStateChange(state.copy(selectedRatio = ratio)) }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        ratio,
                                                        color = if (isSelected) DeepSlate else QabasTheme.current.textPrimary,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = CairoFont
                                                    )
                                                    Text(
                                                        label,
                                                        color = if (isSelected) DeepSlate.copy(alpha = 0.8f) else TextSecondary,
                                                        fontSize = 9.sp,
                                                        fontFamily = NotoSansFont,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Video Duration (طول الفيديو)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        Translator.tr("طول الفيديو:"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "15 ثانية",
                                            "30 ثانية",
                                            "60 ثانية",
                                            "90 ثانية"
                                        ).forEach { dur ->
                                            val isSelected = state.videoDuration == dur
                                            Surface(
                                                color = if (isSelected) PresetPrimary else QabasTheme.current.background,
                                                shape = CutCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (isSelected) PresetPrimary else QabasTheme.current.separator),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { onStateChange(state.copy(videoDuration = dur)) }
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        dur,
                                                        color = if (isSelected) DeepSlate else QabasTheme.current.textPrimary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = CairoFont
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Target Audience (موجه لمن)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        Translator.tr("الجمهور المستهدف (موجه لمن):"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "الشباب ورواد التواصل 📱",
                                            "عامة المسلمين 🌍",
                                            "طلاب العلم والمهتمين 📚",
                                            "غير المسلمين والتعريف بالإسلام 🕊️",
                                            "الناشئة والأطفال 🌟"
                                        ).forEach { audience ->
                                            val isSelected = state.marketingGoal.contains(audience.take(6)) || state.marketingGoal == audience
                                            Box(
                                                modifier = Modifier
                                                    .clip(CutCornerShape(8.dp))
                                                    .background(if (isSelected) QabasTheme.current.accent.copy(alpha = 0.15f) else QabasTheme.current.background)
                                                    .border(1.dp, if (isSelected) PresetPrimary else QabasTheme.current.separator, CutCornerShape(8.dp))
                                                    .clickable { onStateChange(state.copy(marketingGoal = audience)) }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    audience,
                                                    color = if (isSelected) PresetPrimary else TextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontFamily = NotoSansFont
                                                )
                                            }
                                        }
                                    }
                                }

                                // 4. Tone of Content (نبرة المحتوى)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        Translator.tr("نبرة الطرح:"),
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            Translator.tr("خاشع وهادئ 🌿"),
                                            Translator.tr("ملحمي وحماسي ⚡"),
                                            Translator.tr("تأملي عميق 🌌"),
                                            Translator.tr("سرد وثائقي 🎙️")
                                        ).forEach { tone ->
                                            val cleanToneName = tone.split(" ")[0]
                                            val isSelected = state.contentTone.contains(cleanToneName)
                                            Box(
                                                modifier = Modifier
                                                    .clip(CutCornerShape(8.dp))
                                                    .background(if (isSelected) QabasTheme.current.warning.copy(alpha = 0.15f) else QabasTheme.current.background)
                                                    .border(1.dp, if (isSelected) PresetPrimary else QabasTheme.current.separator, CutCornerShape(8.dp))
                                                    .clickable { onStateChange(state.copy(contentTone = cleanToneName)) }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    tone,
                                                    color = if (isSelected) PresetPrimary else TextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontFamily = NotoSansFont
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // QUICK INSPIRATION CAROUSEL
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                Translator.tr("أفكار مقترحة للانطلاق السريع:"),
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (isTrendingLoading) {
                                Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = PresetPrimary, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                val displayIdeas = trendingIdeas ?: emptyList()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    displayIdeas.take(6).forEach { idea ->
                                        Card(
                                            modifier = Modifier
                                                .width(200.dp)
                                                .clickable {
                                                    onStateChange(state.copy(inputText = idea.title + "\n" + idea.description))
                                                    scope.launch { snackbarHostState.showSnackbar(Translator.tr("تم تمكين الفكرة!")) }
                                                },
                                            colors = CardDefaults.cardColors(containerColor = QabasTheme.current.secondarySurface),
                                            border = BorderStroke(1.dp, QabasTheme.current.separator),
                                            shape = CutCornerShape(10.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    if (idea.source == "FALLBACK") "${idea.title} (اقتراح افتراضي)" else idea.title,
                                                    color = PresetPrimary,
                                                    fontFamily = NotoSansFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1
                                                )
                                                Text(idea.description, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, maxLines = 2, lineHeight = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(90.dp))
                    }

            // FLOATING GOLDEN ACTION BUTTON
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = {
                        val text = state.inputText.trim()
                        if (text.isEmpty()) {
                            scope.launch { snackbarHostState.showSnackbar(Translator.tr("اكتب أو تحدث فكرتك أولاً للانتقال للإخراج!")) }
                            return@Button
                        }

                        // Run fast content guard check
                        val inspection = ContentFilterService.quickInspect(text)
                        
                        // Cloud sync & analytics
                        scope.launch {
                            CloudServices.Database.recordContentGuardCheck(
                                text = text,
                                verdict = inspection.verdict.name,
                                score = inspection.score,
                                reason = inspection.reason
                            )
                            AppServices.getAnalyticsService(context).logEvent(
                                "script_guard_check",
                                mapOf(
                                    "verdict" to inspection.verdict.name,
                                    "score" to inspection.score
                                )
                            )
                        }

                        if (inspection.verdict == GuardVerdict.REJECTED) {
                            // Only true explicit violations block the user
                            guardInspectionResult = inspection
                            showGuardDialog = true
                        } else {
                            // APPROPRIATE and NEEDS_REVIEW both proceed to production
                            onProceed()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .shadow(if (state.inputText.isNotBlank()) 16.dp else 0.dp, CutCornerShape(10.dp), spotColor = PresetPrimary),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    shape = CutCornerShape(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (state.inputText.isNotBlank()) goldGradient else androidx.compose.ui.graphics.SolidColor(QabasTheme.current.separator),
                                CutCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (state.videoStyleAnalysis != null) {
                                    val activeName = state.videoStyleAnalysis?.detectedStyle?.ifEmpty { state.editingStyle } ?: ""
                                    Translator.tr("توليد الفيديو بـ (") + activeName + Translator.tr(") 🚀✨")
                                } else {
                                    Translator.tr("تحويل الفكرة إلى مشهد سينمائي 🚀")
                                },
                                color = if (state.inputText.isNotBlank()) DeepSlate else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = if (state.inputText.isNotBlank()) DeepSlate else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            }
            }
        }

        // CONTENT GUARD INSPECTION DIALOG
        if (showGuardDialog && guardInspectionResult != null) {
            val guardRes = guardInspectionResult!!
            val isRejected = guardRes.verdict == GuardVerdict.REJECTED
            val bannerColor = if (isRejected) QabasTheme.current.destructive else QabasTheme.current.warning

            AlertDialog(
                onDismissRequest = { showGuardDialog = false },
                containerColor = QabasTheme.current.cardSurface,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(bannerColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isRejected) Icons.Default.Cancel else Icons.Default.Warning,
                            contentDescription = null,
                            tint = bannerColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = if (isRejected) "تنبيه حارس المحتوى: محتوى مخالف ❌" else "ملاحظة حارس المحتوى: يحتاج تدقيق ⚠️",
                        color = QabasTheme.current.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = guardRes.reason,
                            color = QabasTheme.current.textPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        if (guardRes.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            guardRes.warnings.forEach { w ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = bannerColor, modifier = Modifier.size(8.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(w, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                }
                            }
                        }

                        if (!isRejected && !guardRes.improvedScript.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "الصياغة المقترحة البديلة:",
                                color = PresetPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(QabasTheme.current.background, CutCornerShape(6.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    guardRes.improvedScript!!,
                                    color = QabasTheme.current.textPrimary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (isRejected) {
                        Button(
                            onClick = { showGuardDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = QabasTheme.current.destructive),
                            shape = CutCornerShape(8.dp)
                        ) {
                            Text("تعديل النص ✍️", color = QabasTheme.current.textPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!guardRes.improvedScript.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        onStateChange(state.copy(inputText = guardRes.improvedScript!!))
                                        showGuardDialog = false
                                        onProceed()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                                    shape = CutCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("اعتماد الصياغة المصوبة والمتابعة ✨", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showGuardDialog = false },
                                    modifier = Modifier.weight(1f),
                                    shape = CutCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, QabasTheme.current.separator)
                                ) {
                                    Text("تعديل ✍️", color = QabasTheme.current.textPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                }

                                TextButton(
                                    onClick = {
                                        showGuardDialog = false
                                        onProceed()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("المتابعة على أي حال", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                dismissButton = null
            )
        }

        // VOICE RECORDING OVERLAY
        if (isRecordingVoice) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(30.dp)) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(pulseScale)
                            .background(PresetPrimary.copy(alpha = glowAlpha), CircleShape)
                            .border(2.dp, PresetPrimary, CircleShape)
                            .clickable {
                                scope.launch {
                                    isRecordingVoice = false
                                    onStateChange(
                                        state.copy(
                                            inputText = Translator.tr("فيديو قصير عن أهمية التوكل على الله في الحياة، بأسلوب مؤثر وسينمائي مع التركيز على لقطات طبيعية هادئة وتلاوة قرآنية في الخلفية.")
                                        )
                                    )
                                    snackbarHostState.showSnackbar(Translator.tr("تم تحويل صوتك إلى نص وفكرة بنجاح! 🎤✨"))
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(64.dp))
                    }

                    Text(
                        Translator.tr("تحدث بفكــرتك الآن...\nنحن نستمع إليك بالذكاء الاصطناعي 🎙️"),
                        color = QabasTheme.current.textPrimary,
                        fontSize = 18.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    TextButton(onClick = { isRecordingVoice = false }) {
                        Text(Translator.tr("إلغاء"), color = QabasTheme.current.textPrimary, fontFamily = CairoFont)
                    }
                }
            }
        }
    }
}
}

@Composable
fun SourceTypeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    badgeColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CutCornerShape(12.dp),
        backgroundColor = QabasTheme.current.cardSurface.copy(alpha = 0.85f),
        borderColor = accentColor.copy(alpha = 0.3f)
    ) {
        HudCornerDecor(modifier = Modifier.matchParentSize(), color = accentColor.copy(alpha = 0.1f))
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CutCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        color = QabasTheme.current.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Surface(
                        shape = CutCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            color = badgeColor,
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = QabasTheme.current.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
