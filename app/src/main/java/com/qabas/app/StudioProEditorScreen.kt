package com.qabas.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import java.util.UUID
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioProEditorScreen(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    var projectState by remember { 
        mutableStateOf(EditorProjectState(
            projectId = UUID.randomUUID().toString(), 
            title = "مشروع دعوي احترافي #1",
            tracks = listOf(
                EditorTrack(type = TrackType.TEXT, name = "نصوص", clips = listOf(
                    EditorClip(title = "﴿ رَبِّ زِدْنِي عِلْمًا ﴾", resourceUri = "", startOffsetMs = 2000, durationMs = 5000, trackIndex = 0, type = TrackType.TEXT, color = Color(0xFFFACC15),
                        keyframes = listOf(
                            EditorKeyframe(0, "scale", 0.5f), 
                            EditorKeyframe(1000, "scale", 1.8f), 
                            EditorKeyframe(2500, "scale", 1.4f),
                            EditorKeyframe(0, "opacity", 0f),
                            EditorKeyframe(500, "opacity", 1f),
                            EditorKeyframe(4500, "opacity", 1f),
                            EditorKeyframe(5000, "opacity", 0f)
                        )
                    )
                )),
                EditorTrack(type = TrackType.VIDEO, name = "فيديو", clips = listOf(
                    EditorClip(title = "مقطع الطبيعة", resourceUri = "stock_nature.mp4", startOffsetMs = 0, durationMs = 10000, trackIndex = 1, type = TrackType.VIDEO, color = Color(0xFF3B82F6),
                        keyframes = listOf(EditorKeyframe(0, "opacity", 0f), EditorKeyframe(1000, "opacity", 1f))
                    ),
                    EditorClip(title = "تراكم سحب", resourceUri = "clouds.mp4", startOffsetMs = 10000, durationMs = 8000, trackIndex = 1, type = TrackType.VIDEO, color = Color(0xFF3B82F6))
                )),
                EditorTrack(type = TrackType.AUDIO, name = "صوت", clips = listOf(
                    EditorClip(title = "تلاوة خاشعة", resourceUri = "recitation.mp3", startOffsetMs = 0, durationMs = 15000, trackIndex = 2, type = TrackType.AUDIO, color = Color(0xFF10B981))
                ))
            )
        )) 
    }
    
    val history = remember { mutableStateListOf<EditorProjectState>() }
    val redoHistory = remember { mutableStateListOf<EditorProjectState>() }

    fun saveToHistory() {
        history.add(projectState.copy())
        if (history.size > 20) history.removeAt(0)
        redoHistory.clear()
    }
    
    var showMediaBrowser by remember { mutableStateOf(false) }
    var showLayerManager by remember { mutableStateOf(false) }
    var showAdjustPanel by remember { mutableStateOf(false) }
    var showTransitionPicker by remember { mutableStateOf(false) }
    var showAiMagicPanel by remember { mutableStateOf(false) }
    var selectedTransitionClipId by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val autoSaveStatus by WorkspaceAutoSaveManager.status.collectAsState()

    // 1. Initial check and restore from Room Database if a saved draft exists
    LaunchedEffect(Unit) {
        WorkspaceAutoSaveManager.init(context)
        val draft = WorkspaceAutoSaveManager.getProDraft()
        if (draft != null) {
            val restored = WorkspaceAutoSaveManager.deserializeProState(draft.stateJson)
            if (restored != null && restored.tracks.isNotEmpty()) {
                projectState = restored
                Toast.makeText(context, "تمت استعادة مسودة المونتاج المحفوظة تلقائياً 🎬", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Periodic background auto-save to Room database every 15 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            kotlinx.coroutines.delay(15000L)
            WorkspaceAutoSaveManager.performProEditorSave(projectState)
        }
    }

    // 3. Debounced auto-save on state modification
    LaunchedEffect(projectState) {
        WorkspaceAutoSaveManager.scheduleProEditorSave(projectState, debounceMs = 1500L)
    }
    
    // Playback Logic
    LaunchedEffect(projectState.isPlaying) {
        if (projectState.isPlaying) {
            while (projectState.isPlaying && projectState.currentTimeMs < projectState.totalDurationMs) {
                kotlinx.coroutines.delay(16) // ~60fps
                projectState = projectState.copy(currentTimeMs = projectState.currentTimeMs + 16)
            }
            if (projectState.currentTimeMs >= projectState.totalDurationMs) {
                projectState = projectState.copy(isPlaying = false, currentTimeMs = 0)
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F0F0F),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(projectState.title, color = Color.White, fontSize = 14.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (projectState.isPlaying) Color.Red else Color.Gray))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                formatTime(projectState.currentTimeMs) + " / " + formatTime(projectState.totalDurationMs),
                                color = GoldPrimary, fontSize = 10.sp, fontFamily = NotoSansFont
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (autoSaveStatus.isSaving) GoldPrimary.copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Icon(
                                    imageVector = if (autoSaveStatus.isSaving) Icons.Default.Sync else Icons.Default.Save,
                                    contentDescription = null,
                                    tint = if (autoSaveStatus.isSaving) GoldPrimary else Color(0xFF10B981),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (autoSaveStatus.isSaving) "جاري الحفظ..." else "محفوظ Room",
                                    color = if (autoSaveStatus.isSaving) GoldPrimary else Color(0xFF10B981),
                                    fontSize = 9.sp,
                                    fontFamily = NotoSansFont
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = GoldPrimary)
                        }
                        
                        // Undo/Redo Actions
                        IconButton(
                            onClick = {
                                if (history.isNotEmpty()) {
                                    redoHistory.add(projectState.copy())
                                    projectState = history.removeAt(history.size - 1)
                                    Toast.makeText(context, "تراجع (Undo) ↩️", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = history.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Undo, null, tint = if (history.isNotEmpty()) GoldPrimary else Color.Gray)
                        }
                        
                        IconButton(
                            onClick = {
                                if (redoHistory.isNotEmpty()) {
                                    history.add(projectState.copy())
                                    projectState = redoHistory.removeAt(redoHistory.size - 1)
                                    Toast.makeText(context, "إعادة (Redo) ↪️", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = redoHistory.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Redo, null, tint = if (redoHistory.isNotEmpty()) GoldPrimary else Color.Gray)
                        }
                    }
                },
                actions = {
                    // PC-like Workspace Selector
                    TextButton(onClick = { 
                        val nextMode = when(projectState.workspaceMode) {
                            WorkspaceMode.EDIT -> WorkspaceMode.COLOR
                            WorkspaceMode.COLOR -> WorkspaceMode.AUDIO
                            WorkspaceMode.AUDIO -> WorkspaceMode.EXPORT
                            WorkspaceMode.EXPORT -> WorkspaceMode.EDIT
                        }
                        projectState = projectState.copy(workspaceMode = nextMode)
                        if (nextMode == WorkspaceMode.COLOR) showAdjustPanel = true
                    }) {
                        Text(
                            when(projectState.workspaceMode) {
                                WorkspaceMode.EDIT -> "المونتاج 🎞️"
                                WorkspaceMode.COLOR -> "التلوين 🎨"
                                WorkspaceMode.AUDIO -> "الصوتيات 🔊"
                                WorkspaceMode.EXPORT -> "التصدير 🚀"
                            },
                            color = GoldPrimary, fontSize = 12.sp, fontFamily = CairoFont
                        )
                    }
                    
                    IconButton(onClick = { 
                        projectState = projectState.copy(isSmartCaptionsEnabled = !projectState.isSmartCaptionsEnabled)
                        Toast.makeText(context, if (projectState.isSmartCaptionsEnabled) "تم تفعيل الكابشنز الذكية ✨" else "تم إيقاف الكابشنز الذكية", Toast.LENGTH_SHORT).show()
                    }) { 
                        Icon(
                            Icons.Default.TextFields, 
                            null, 
                            tint = if (projectState.isSmartCaptionsEnabled) GoldPrimary else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        ) 
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp).padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.IosShare, null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تصدير", color = DeepSlate, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            
            // 1. Preview Area (Pro Viewport)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Background Pattern
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 40.dp.toPx()
                    for (x in 0..(size.width / step).toInt()) {
                        drawLine(Color.White.copy(alpha = 0.03f), Offset(x * step, 0f), Offset(x * step, size.height))
                    }
                    for (y in 0..(size.height / step).toInt()) {
                        drawLine(Color.White.copy(alpha = 0.03f), Offset(0f, y * step), Offset(size.width, y * step))
                    }
                }

                // Video Container
                Surface(
                    modifier = Modifier.fillMaxHeight(0.9f).aspectRatio(9f/16f),
                    color = Color(0xFF1A1A1A),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Placeholder for video content
                        Icon(Icons.Default.Movie, null, tint = Color.White.copy(alpha = 0.05f), modifier = Modifier.size(120.dp))
                        
                        // Active Text Layer or Kinetic Captions
                        val activeTextClip = projectState.tracks.firstOrNull { it.type == TrackType.TEXT }?.clips?.firstOrNull { 
                            projectState.currentTimeMs >= it.startOffsetMs && projectState.currentTimeMs <= it.startOffsetMs + it.durationMs
                        }
                        
                        val activeVideoClip = projectState.tracks.firstOrNull { it.type == TrackType.VIDEO }?.clips?.firstOrNull { 
                            projectState.currentTimeMs >= it.startOffsetMs && projectState.currentTimeMs <= it.startOffsetMs + it.durationMs
                        }

                        // Simulated Video Background with Filters and Transforms
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    activeVideoClip?.let { clip ->
                                        // Apply Keyframe Interpolation for Video Properties if they exist
                                        val relativeTime = projectState.currentTimeMs - clip.startOffsetMs
                                        val scale = SmartFeaturesEngine.interpolateProperty(clip.keyframes, relativeTime, "scale", 1f)
                                        val opacity = SmartFeaturesEngine.interpolateProperty(clip.keyframes, relativeTime, "opacity", 1f)
                                        
                                        alpha = (clip.filters.brightness.coerceIn(0f, 1f)) * opacity
                                        scaleX = clip.transform.scaleX * scale
                                        scaleY = clip.transform.scaleY * scale
                                        rotationZ = clip.transform.rotation
                                        translationX = clip.transform.posX
                                        translationY = clip.transform.posY
                                    }
                                }
                        ) {
                            Icon(
                                Icons.Default.Movie, 
                                null, 
                                tint = activeVideoClip?.color?.copy(alpha = 0.1f) ?: Color.White.copy(alpha = 0.05f), 
                                modifier = Modifier.size(120.dp).align(Alignment.Center)
                            )
                        }

                        if (projectState.isSmartCaptionsEnabled) {
                            val activeCaptions = remember(projectState.currentTimeMs, activeTextClip?.id) {
                                SmartFeaturesEngine.generateKineticCaptions(
                                    activeTextClip?.title ?: "﴿ رَبِّ زِدْنِي عِلْمًا ﴾", 
                                    activeTextClip?.durationMs ?: 5000L
                                )
                            }
                            // Simplified simulation of word highlight based on time
                            val currentWord = activeCaptions.firstOrNull { 
                                val relativeTime = projectState.currentTimeMs % 5000L
                                relativeTime >= it.startTimeMs && relativeTime <= it.endTimeMs 
                            } ?: activeCaptions.first()

                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                                Text(
                                    currentWord.text,
                                    color = currentWord.color,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                                Text(
                                    "AI Kinetic Style: ${currentWord.style.name}",
                                    color = GoldPrimary.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontFamily = RobotoMonoFont
                                )
                            }
                        } else {
                            val currentScale = activeTextClip?.let { 
                                SmartFeaturesEngine.interpolateProperty(it.keyframes, projectState.currentTimeMs - it.startOffsetMs, "scale", 1f)
                            } ?: 1f
                            val currentOpacity = activeTextClip?.let { 
                                SmartFeaturesEngine.interpolateProperty(it.keyframes, projectState.currentTimeMs - it.startOffsetMs, "opacity", 1f)
                            } ?: 1f

                            Text(
                                activeTextClip?.title ?: "﴿ رَبِّ زِدْنِي عِلْمًا ﴾",
                                color = Color.White.copy(alpha = currentOpacity),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = (20 * currentScale).sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
                
                // Keyframe Control Buttons (Overlay)
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                saveToHistory()
                                // Logic to add keyframe to selected clip
                                Toast.makeText(context, "تمت إضافة مفتاح حركة (Keyframe) 💎", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape).border(1.dp, GoldPrimary, CircleShape)
                        ) {
                            Icon(Icons.Default.AddLocation, null, tint = GoldPrimary)
                        }
                    }
                }

                // AI Viral Scorer Overlay (High Attraction Feature)
                if (projectState.workspaceMode == WorkspaceMode.EXPORT) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Viral Potential", color = Color.Gray, fontSize = 10.sp, fontFamily = RobotoMonoFont)
                            Text("94%", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFamily = RobotoMonoFont)
                            Text("ذكاء قبس يتوقع انتشاراً واسعاً! ✨", color = Color.White, fontSize = 10.sp, fontFamily = CairoFont)
                        }
                    }
                }
                
                // Playback Controls Overlay
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {}) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) }
                    
                    Surface(
                        onClick = { projectState = projectState.copy(isPlaying = !projectState.isPlaying) },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (projectState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    
                    IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                }
            }

            // 2. Pro Timeline Section
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = Color(0xFF121212)
            ) {
                Column {
                    // Timeline Tools & Ruler
                    Row(
                        modifier = Modifier.fillMaxWidth().height(32.dp).background(Color(0xFF1A1A1A)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                            Icon(
                                Icons.Default.Layers, null, 
                                tint = if (showLayerManager) GoldPrimary else Color.Gray, 
                                modifier = Modifier.size(14.dp).clickable { showLayerManager = !showLayerManager }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                if (projectState.isMagneticTimeline) Icons.Default.Link else Icons.Default.LinkOff, 
                                null, 
                                tint = if (projectState.isMagneticTimeline) GoldPrimary else Color.Gray, 
                                modifier = Modifier.size(14.dp).clickable { 
                                    projectState = projectState.copy(isMagneticTimeline = !projectState.isMagneticTimeline)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("المغناطيسي", color = if (projectState.isMagneticTimeline) Color.White else Color.Gray, fontSize = 9.sp, fontFamily = CairoFont)
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            // Zoom Control
                            Icon(Icons.Default.ZoomOut, null, tint = Color.Gray, modifier = Modifier.size(12.dp).clickable { projectState = projectState.copy(zoomLevel = (projectState.zoomLevel * 0.8f).coerceAtLeast(0.5f)) })
                            Text("${(projectState.zoomLevel * 100).toInt()}%", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
                            Icon(Icons.Default.ZoomIn, null, tint = Color.Gray, modifier = Modifier.size(12.dp).clickable { projectState = projectState.copy(zoomLevel = (projectState.zoomLevel * 1.2f).coerceAtMost(5f)) })
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(projectState.zoomLevel) {
                                    detectTapGestures { offset ->
                                        val clickedMs = (offset.x / projectState.zoomLevel * 10f).toLong()
                                        projectState = projectState.copy(currentTimeMs = clickedMs.coerceIn(0, projectState.totalDurationMs))
                                    }
                                }
                        ) {
                            TimelineRuler(projectState.totalDurationMs, projectState.zoomLevel)
                        }
                        
                        Text(formatTime(projectState.currentTimeMs), color = GoldPrimary, fontSize = 10.sp, modifier = Modifier.padding(end = 12.dp), fontFamily = NotoSansFont)
                    }
                    
                    // Scrollable Tracks Area
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(projectState.zoomLevel) {
                                detectTapGestures { offset ->
                                    val clickedMs = (offset.x / projectState.zoomLevel * 10f).toLong()
                                    projectState = projectState.copy(currentTimeMs = clickedMs.coerceIn(0, projectState.totalDurationMs))
                                }
                            }
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            projectState.tracks.forEach { track ->
                                TimelineTrackRow(
                                    track = track, 
                                    zoom = projectState.zoomLevel,
                                    selectedClipId = projectState.selectedClipId,
                                    onClipClick = { clipId -> 
                                        projectState = projectState.copy(selectedClipId = clipId)
                                    },
                                    onTransitionClick = { clipId ->
                                        selectedTransitionClipId = clipId
                                        showTransitionPicker = true
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(100.dp)) // Extra space at bottom
                        }
                        
                        // Current Time Indicator (Scrubber line)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .background(GoldPrimary)
                                .offset(x = (projectState.currentTimeMs.toFloat() / 10f * projectState.zoomLevel).dp)
                        ) {
                            // Top Handle
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(GoldPrimary).align(Alignment.TopCenter))
                        }
                    }
                }
            }

            // 3. Pro Toolbelt (Bottom)
            Surface(
                modifier = Modifier.fillMaxWidth().height(90.dp),
                color = Color.Black,
                tonalElevation = 12.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolButton(Icons.Default.AddCircle, "استيراد", onClick = { showMediaBrowser = true })
                    ToolButton(
                        icon = Icons.Default.ContentCut, 
                        label = "تقسيم", 
                        onClick = {
                            saveToHistory()
                            val clipToSplit = projectState.tracks.flatMap { it.clips }.find { it.id == projectState.selectedClipId }
                            if (clipToSplit != null) {
                                val relativeTime = projectState.currentTimeMs - clipToSplit.startOffsetMs
                                if (relativeTime > 0 && relativeTime < clipToSplit.durationMs) {
                                    val secondPart = clipToSplit.copy(
                                        id = UUID.randomUUID().toString(),
                                        startOffsetMs = projectState.currentTimeMs,
                                        durationMs = clipToSplit.durationMs - relativeTime
                                    )
                                    val firstPart = clipToSplit.copy(durationMs = relativeTime)
                                    
                                    projectState = projectState.copy(
                                        tracks = projectState.tracks.map { track ->
                                            if (track.clips.any { it.id == clipToSplit.id }) {
                                                track.copy(clips = track.clips.flatMap { 
                                                    if (it.id == clipToSplit.id) listOf(firstPart, secondPart) else listOf(it)
                                                })
                                            } else track
                                        }
                                    )
                                    Toast.makeText(context, "تم تقسيم المقطع ✂️", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = projectState.selectedClipId != null
                    )
                    ToolButton(Icons.Default.Audiotrack, "صوتيات", onClick = {})
                    ToolButton(Icons.Default.TextFields, "نصوص", onClick = {})
                    ToolButton(Icons.Default.AutoAwesome, "تأثيرات", onClick = {})
                    ToolButton(Icons.Default.Animation, "تحريك", onClick = {})
                    ToolButton(Icons.Default.AutoFixHigh, "سحر AI", onClick = { showAiMagicPanel = true })
                    ToolButton(Icons.Default.ContentCopy, "ملصقات", onClick = { showMediaBrowser = true })
                    ToolButton(Icons.Default.Tune, "ضبط", onClick = { showAdjustPanel = true })
                }
            }
        }
    }

    // AI Magic Panel
    if (showAiMagicPanel) {
        ModalBottomSheet(
            onDismissRequest = { showAiMagicPanel = false },
            containerColor = Color(0xFF0F0F0F)
        ) {
            AiMagicPanel(
                onAutoSubtitles = {
                    saveToHistory()
                    projectState = projectState.copy(isSmartCaptionsEnabled = true)
                    showAiMagicPanel = false
                    Toast.makeText(context, "جاري توليد الكابشنز الذكية... ✨", Toast.LENGTH_LONG).show()
                },
                onRemoveBg = {
                    Toast.makeText(context, "ميزة إزالة الخلفية بالذكاء الاصطناعي قيد المعالجة... 🪄", Toast.LENGTH_LONG).show()
                    showAiMagicPanel = false
                }
            )
        }
    }

    // Export Panel Mode UI
    if (projectState.workspaceMode == WorkspaceMode.EXPORT) {
        ModalBottomSheet(
            onDismissRequest = { projectState = projectState.copy(workspaceMode = WorkspaceMode.EDIT) },
            containerColor = Color(0xFF0F0F0F)
        ) {
            ExportWorkspace(onExport = { onSave() })
        }
    }

    // Audio Mixer Mode UI
    if (projectState.workspaceMode == WorkspaceMode.AUDIO) {
        ModalBottomSheet(
            onDismissRequest = { projectState = projectState.copy(workspaceMode = WorkspaceMode.EDIT) },
            containerColor = Color(0xFF0F0F0F)
        ) {
            AudioMixerWorkspace(projectState) { updatedTracks ->
                projectState = projectState.copy(tracks = updatedTracks)
            }
        }
    }

    // Layer Manager
    if (showLayerManager) {
        ModalBottomSheet(
            onDismissRequest = { showLayerManager = false },
            containerColor = Color(0xFF1A1A1A)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text("إدارة الطبقات (Layer Manager)", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                projectState.tracks.forEach { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when(track.type) {
                                    TrackType.VIDEO -> Icons.Default.VideoLibrary
                                    TrackType.AUDIO -> Icons.Default.MusicNote
                                    TrackType.TEXT -> Icons.Default.Title
                                    else -> Icons.Default.Layers
                                },
                                null, tint = GoldPrimary, modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(track.name, color = Color.White, fontFamily = CairoFont)
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconButton(onClick = {}) { 
                                Icon(Icons.Default.Visibility, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) 
                            }
                            IconButton(onClick = {}) { 
                                Icon(Icons.Default.VolumeUp, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) 
                            }
                            IconButton(onClick = {}) { 
                                Icon(Icons.Default.LockOpen, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) 
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showLayerManager = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("إغلاق", color = DeepSlate, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                }
            }
        }
    }

    // Transition Picker
    if (showTransitionPicker) {
        ModalBottomSheet(
            onDismissRequest = { showTransitionPicker = false },
            containerColor = Color(0xFF1A1A1A)
        ) {
            TransitionPicker(
                onTransitionSelected = { transitionName ->
                    saveToHistory()
                    projectState = projectState.copy(
                        tracks = projectState.tracks.map { track ->
                            track.copy(clips = track.clips.map { clip ->
                                if (clip.id == selectedTransitionClipId) clip.copy(transitionIn = transitionName) else clip
                            })
                        }
                    )
                    showTransitionPicker = false
                    Toast.makeText(context, "تم تطبيق انتقال: $transitionName ✨", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    
    // Adjust Panel (Color Grading)
    if (showAdjustPanel) {
        val currentClip = projectState.tracks.flatMap { it.clips }.find { it.id == projectState.selectedClipId }
        
        ModalBottomSheet(
            onDismissRequest = { showAdjustPanel = false },
            containerColor = Color(0xFF1A1A1A)
        ) {
            AdjustPanel(
                filters = currentClip?.filters ?: FilterParams(),
                onFiltersChanged = { newFilters ->
                    saveToHistory()
                    projectState = projectState.copy(
                        tracks = projectState.tracks.map { track ->
                            track.copy(clips = track.clips.map { clip ->
                                if (clip.id == projectState.selectedClipId) clip.copy(filters = newFilters) else clip
                            })
                        }
                    )
                },
                onDelete = {
                    saveToHistory()
                    val clipToDelete = projectState.tracks.flatMap { it.clips }.find { it.id == projectState.selectedClipId }
                    projectState = projectState.copy(
                        tracks = projectState.tracks.map { track ->
                            val updatedClips = track.clips.filter { it.id != projectState.selectedClipId }
                            
                            if (projectState.isMagneticTimeline && clipToDelete != null && track.clips.any { it.id == clipToDelete.id }) {
                                // Re-calculate offsets for magnetic effect
                                var currentOffset = 0L
                                track.copy(clips = updatedClips.sortedBy { it.startOffsetMs }.map { clip ->
                                    val newClip = clip.copy(startOffsetMs = currentOffset)
                                    currentOffset += clip.durationMs
                                    newClip
                                })
                            } else {
                                track.copy(clips = updatedClips)
                            }
                        },
                        selectedClipId = null
                    )
                    showAdjustPanel = false
                    Toast.makeText(context, if (projectState.isMagneticTimeline) "تم الحذف والدمج المغناطيسي 🧲" else "تم حذف المقطع 🗑️", Toast.LENGTH_SHORT).show()
                },
                onDuplicate = {
                    saveToHistory()
                    val clipToDup = projectState.tracks.flatMap { it.clips }.find { it.id == projectState.selectedClipId }
                    if (clipToDup != null) {
                        val dup = clipToDup.copy(
                            id = UUID.randomUUID().toString(), 
                            startOffsetMs = clipToDup.startOffsetMs + clipToDup.durationMs
                        )
                        projectState = projectState.copy(
                            tracks = projectState.tracks.map { track ->
                                if (track.clips.any { it.id == clipToDup.id }) {
                                    track.copy(clips = track.clips + dup)
                                } else track
                            }
                        )
                        Toast.makeText(context, "تم تكرار المقطع 📑", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showAdjustPanel = false }
            )
        }
    }

    // Media Browser Bottom Sheet (Asset Manager)
    if (showMediaBrowser) {
        ModalBottomSheet(
            onDismissRequest = { showMediaBrowser = false },
            containerColor = Color(0xFF1A1A1A),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            MediaAssetBrowser(
                onAssetSelected = { asset ->
                    saveToHistory()
                    val trackType = when(asset.type) {
                        "video" -> TrackType.VIDEO
                        "audio" -> TrackType.AUDIO
                        else -> TrackType.OVERLAY
                    }
                    val newClip = EditorClip(
                        title = asset.title,
                        resourceUri = asset.url,
                        startOffsetMs = projectState.currentTimeMs,
                        durationMs = asset.durationSeconds * 1000L,
                        trackIndex = if (trackType == TrackType.VIDEO) 1 else 2,
                        type = trackType,
                        color = if (trackType == TrackType.VIDEO) Color(0xFF3B82F6) else Color(0xFF10B981)
                    )
                    
                    projectState = projectState.copy(
                        tracks = projectState.tracks.map { track ->
                            if (track.type == trackType) track.copy(clips = track.clips + newClip) else track
                        }
                    )
                    showMediaBrowser = false
                    Toast.makeText(context, "تمت إضافة المقطع للتايم لاين ✨", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun TimelineRuler(totalMs: Long, zoom: Float) {
    Canvas(modifier = Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF1E1E1E))) {
        val step = 1000L // Use Long
        for (i in 0L..totalMs step step) {
            val x = (i.toFloat() / 10f) * zoom
            drawLine(
                color = Color.Gray,
                start = Offset(x, 0f),
                end = Offset(x, if (i % 5000L == 0L) 15f else 8f),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
fun TimelineTrackRow(
    track: EditorTrack, 
    zoom: Float,
    selectedClipId: String?,
    onClipClick: (String) -> Unit,
    onTransitionClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).border(0.5.dp, Color.White.copy(alpha = 0.05f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Icon/Label (Layer Info)
        Box(
            modifier = Modifier.width(40.dp).fillMaxHeight().background(Color(0xFF151515)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                when(track.type) {
                    TrackType.VIDEO -> Icons.Default.VideoLibrary
                    TrackType.AUDIO -> Icons.Default.MusicNote
                    TrackType.TEXT -> Icons.Default.Title
                    else -> Icons.Default.Layers
                },
                null, tint = if (selectedClipId != null && track.clips.any { it.id == selectedClipId }) GoldPrimary else Color.Gray, modifier = Modifier.size(16.dp)
            )
        }
        
        // Track Content Area
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
            track.clips.forEach { clip ->
                val isSelected = clip.id == selectedClipId
                Surface(
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .width((clip.durationMs.toFloat() / 10f * zoom).dp)
                        .offset(x = (clip.startOffsetMs.toFloat() / 10f * zoom).dp)
                        .align(Alignment.CenterStart)
                        .clickable { onClipClick(clip.id) },
                    color = if (isSelected) clip.color.copy(alpha = 0.9f) else clip.color.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isSelected) 2.dp else 1.dp, 
                        if (isSelected) Color.White else clip.color
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (track.type == TrackType.AUDIO) {
                            // Simulated Waveform
                            val waveformPoints = remember(clip.resourceUri, clip.durationMs) {
                                SmartFeaturesEngine.generateWaveformData(clip.resourceUri, (clip.durationMs / 200).toInt().coerceAtLeast(10))
                            }
                            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                                val barWidth = size.width / waveformPoints.size
                                waveformPoints.forEachIndexed { i, peak ->
                                    val h = peak * size.height * 0.8f
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.4f),
                                        topLeft = Offset(i * barWidth, (size.height - h) / 2),
                                        size = androidx.compose.ui.geometry.Size(barWidth - 1.dp.toPx(), h)
                                    )
                                }
                            }
                        }
                        
                        Text(clip.title, fontSize = 8.sp, color = Color.White, modifier = Modifier.padding(4.dp), maxLines = 1)
                        
                        // Keyframe Markers (Diamonds)
                        clip.keyframes.forEach { kf ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .offset(x = (kf.timeMs.toFloat() / 10f * zoom).dp)
                                    .align(Alignment.BottomStart)
                                    .rotate(45f)
                                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
                
                // Transition Button Indicator (Simulated)
                if (clip.startOffsetMs + clip.durationMs < 30000) { // If not last
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .offset(x = ((clip.startOffsetMs + clip.durationMs).toFloat() / 10f * zoom - 7f).dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .align(Alignment.CenterStart)
                            .clickable { onTransitionClick(clip.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AiMagicPanel(onAutoSubtitles: () -> Unit, onRemoveBg: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("سحر الذكاء الاصطناعي ✨", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))
        
        MagicOption(
            title = "كابشنز تلقائية (Auto Captions)",
            description = "توليد نصوص متحركة متزامنة مع الصوت بذكاء.",
            icon = Icons.Default.TextFields,
            onClick = onAutoSubtitles
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        MagicOption(
            title = "إزالة الخلفية (Remove Background)",
            description = "عزل المتحدث عن الخلفية بضغطة واحدة.",
            icon = Icons.Default.Person,
            onClick = onRemoveBg
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        MagicOption(
            title = "تحسين الصوت (Audio Enhance)",
            description = "إزالة الضجيج وتحسين نبرة الصوت الدعوي.",
            icon = Icons.Default.Mic,
            onClick = { /* Simulated */ }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun MagicOption(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(GoldPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 14.sp)
                Text(description, color = Color.Gray, fontSize = 11.sp, fontFamily = NotoSansFont)
            }
            Icon(Icons.Default.ChevronLeft, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun TransitionPicker(onTransitionSelected: (String) -> Unit) {
    val transitions = listOf(
        "Dissolve" to Icons.Default.AutoAwesome,
        "Fade Black" to Icons.Default.Brightness1,
        "Zoom In" to Icons.Default.ZoomIn,
        "Slide Right" to Icons.AutoMirrored.Filled.ArrowForward,
        "Glitch" to Icons.Default.FlashOn
    )
    
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("اختر تأثير الانتقال ✨", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(20.dp))
        
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(transitions) { (name, icon) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable { onTransitionSelected(name) }
                        .padding(16.dp)
                ) {
                    Icon(icon, null, tint = GoldPrimary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(name, color = Color.White, fontSize = 10.sp, fontFamily = CairoFont)
                }
            }
        }
    }
}

@Composable
fun ExportWorkspace(onExport: () -> Unit) {
    var selectedPreset by remember { mutableStateOf("TikTok / Reels") }
    val presets = listOf("TikTok / Reels", "YouTube Shorts", "Instagram Post", "Original (9:16)")
    
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("تصدير العمل النهائي", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("اختر المنصة المستهدفة لتحسين الجودة", color = Color.Gray, fontSize = 12.sp, fontFamily = CairoFont)
        Spacer(modifier = Modifier.height(12.dp))
        
        presets.forEach { preset ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { selectedPreset = preset },
                color = if (selectedPreset == preset) GoldPrimary.copy(alpha = 0.1f) else Color(0xFF1A1A1A),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedPreset == preset) GoldPrimary else Color.Transparent)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (selectedPreset == preset) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, 
                        null, tint = if (selectedPreset == preset) GoldPrimary else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(preset, color = Color.White, fontFamily = CairoFont)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("إعدادات متقدمة", color = Color.Gray, fontSize = 12.sp, fontFamily = CairoFont)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("دقة الفيديو", color = Color.White, fontSize = 14.sp)
            Text("1080p (Full HD)", color = GoldPrimary, fontSize = 14.sp)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("معدل الإطارات", color = Color.White, fontSize = 14.sp)
            Text("60 FPS", color = GoldPrimary, fontSize = 14.sp)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
        ) {
            Icon(Icons.Default.Upload, null, tint = DeepSlate)
            Spacer(modifier = Modifier.width(12.dp))
            Text("تصدير ونشر الآن 🚀", color = DeepSlate, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 16.sp)
        }
    }
}

@Composable
fun AudioMixerWorkspace(state: EditorProjectState, onTracksUpdated: (List<EditorTrack>) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("ميكسر الصوت الاحترافي (Audio Mixer)", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))
        
        state.tracks.filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }.forEach { track ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(track.name, color = Color.White, fontFamily = CairoFont)
                    Text("${(0.8f * 100).toInt()}%", color = GoldPrimary, fontSize = 12.sp)
                }
                Slider(
                    value = 0.8f,
                    onValueChange = { },
                    colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("كتم (Mute)", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.clickable { })
                    Text("منفرد (Solo)", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.clickable { })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { /* Master EQ logic */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Icon(Icons.Default.GraphicEq, null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تعديل الـ Master EQ الذكي", color = Color.White, fontFamily = CairoFont)
        }
    }
}

@Composable
fun MediaAssetBrowser(onAssetSelected: (MediaAsset) -> Unit) {
    var selectedCategory by remember { mutableStateOf("فيديو") }
    val categories = listOf("فيديو", "صور", "صوت", "مواردي", "خطافات (Hooks)")
    val centralAssets by CentralMediaManager.assets.collectAsState()
    
    val realAssets = remember(selectedCategory, centralAssets) {
        when (selectedCategory) {
            "فيديو" -> {
                val brolls = BRollEngine.library.map { b ->
                    MediaAsset(
                        id = b.id,
                        title = b.title,
                        url = b.videoUrl,
                        thumbnailUrl = b.thumbnailUrl,
                        durationSeconds = 10,
                        type = "video"
                    )
                }
                val centralVideos = centralAssets.filter { it.type == CentralMediaType.VIDEO }.map { c ->
                    MediaAsset(
                        id = c.id,
                        title = c.title,
                        url = c.displayUrl,
                        thumbnailUrl = "",
                        durationSeconds = 15,
                        type = "video"
                    )
                }
                (brolls + centralVideos).distinctBy { it.id }
            }
            "صور" -> {
                centralAssets.filter { it.type == CentralMediaType.IMAGE }.map { c ->
                    MediaAsset(
                        id = c.id,
                        title = c.title,
                        url = c.displayUrl,
                        thumbnailUrl = c.displayUrl,
                        durationSeconds = 5,
                        type = "image"
                    )
                }
            }
            "صوت" -> {
                centralAssets.filter { it.type == CentralMediaType.AUDIO }.map { c ->
                    MediaAsset(
                        id = c.id,
                        title = c.title,
                        url = c.displayUrl,
                        thumbnailUrl = "",
                        durationSeconds = 30,
                        type = "audio"
                    )
                }
            }
            "مواردي" -> {
                centralAssets.map { c ->
                    MediaAsset(
                        id = c.id,
                        title = c.title,
                        url = c.displayUrl,
                        thumbnailUrl = if (c.type == CentralMediaType.IMAGE) c.displayUrl else "",
                        durationSeconds = 10,
                        type = when (c.type) {
                            CentralMediaType.VIDEO -> "video"
                            CentralMediaType.AUDIO -> "audio"
                            else -> "image"
                        }
                    )
                }
            }
            "خطافات (Hooks)" -> {
                BRollEngine.library.filter { b -> b.keywords.any { t -> t.contains("مكة", ignoreCase = true) || t.contains("طواف", ignoreCase = true) || t.contains("مسجد", ignoreCase = true) } }.map { b ->
                    MediaAsset(
                        id = b.id,
                        title = b.title,
                        url = b.videoUrl,
                        thumbnailUrl = b.thumbnailUrl,
                        durationSeconds = 4,
                        type = "video"
                    )
                }
            }
            else -> emptyList()
        }
    }
    
    Column(modifier = Modifier.fillMaxWidth().height(400.dp).padding(16.dp)) {
        Text("متصفح الموارد الحقيقية (Asset Library)", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
        
        LazyRow(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontFamily = CairoFont, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldPrimary)
                )
            }
        }
        
        if (realAssets.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("لا توجد وسائط مضافة حالياً في هذا القسم — يمكنك التوليد أو الرفع المباشر", color = Color.Gray, fontSize = 12.sp, fontFamily = CairoFont)
            }
        } else {
            // Asset Grid
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(realAssets) { asset ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2A2A))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .clickable { onAssetSelected(asset) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                            Icon(
                                when (asset.type) {
                                    "video" -> Icons.Default.Movie
                                    "audio" -> Icons.Default.Audiotrack
                                    else -> Icons.Default.Image
                                }, 
                                null, 
                                tint = GoldPrimary.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(asset.title, color = Color.White, fontSize = 10.sp, fontFamily = CairoFont, textAlign = TextAlign.Center, maxLines = 1)
                            Text("${asset.durationSeconds}s", color = Color.Gray, fontSize = 8.sp, fontFamily = NotoSansFont)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdjustPanel(
    filters: FilterParams, 
    onFiltersChanged: (FilterParams) -> Unit,
    onDelete: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("الألوان") }
    
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("مفتش الخصائص (Inspector)", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("الألوان", "التحويل", "المزج").forEach { tab ->
                    Text(
                        tab,
                        color = if (selectedTab == tab) GoldPrimary else Color.Gray,
                        modifier = Modifier.clickable { selectedTab = tab },
                        fontSize = 12.sp,
                        fontFamily = CairoFont
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        when (selectedTab) {
            "الألوان" -> {
                AdjustSlider("السطوع (Brightness)", filters.brightness, 0f..2f) { onFiltersChanged(filters.copy(brightness = it)) }
                AdjustSlider("التباين (Contrast)", filters.contrast, 0f..2f) { onFiltersChanged(filters.copy(contrast = it)) }
                AdjustSlider("التشبع (Saturation)", filters.saturation, 0f..2f) { onFiltersChanged(filters.copy(saturation = it)) }
                AdjustSlider("الحدة (Sharpness)", filters.sharpness, 0f..1f) { onFiltersChanged(filters.copy(sharpness = it)) }
            }
            "التحويل" -> {
                Text("الأبعاد والموقع (Desktop Precision)", color = GoldPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                Spacer(modifier = Modifier.height(12.dp))
                // Transform Sliders (Simulated Desktop-like controls)
                AdjustSlider("الحجم X", 1.0f, 0.1f..3f) {}
                AdjustSlider("الحجم Y", 1.0f, 0.1f..3f) {}
                AdjustSlider("التدوير", 0f, -180f..180f) {}
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("السرعة (Speed Ramping)", color = GoldPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                AdjustSlider("سرعة المقطع", 1.0f, 0.1f..5f) {}
            }
            "المزج" -> {
                Text("أنماط المزج (Blend Modes)", color = GoldPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                Spacer(modifier = Modifier.height(12.dp))
                val modes = listOf("Normal", "Overlay", "Screen", "Multiply", "Darken")
                modes.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(mode, color = Color.White, fontSize = 14.sp)
                        if (mode == "Normal") Icon(Icons.Default.Check, null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        
        // Volume & Transitions Section (New)
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("مستوى الصوت", color = Color.Gray, fontSize = 12.sp, fontFamily = CairoFont)
                Slider(
                    value = 0.8f, // Simulated current clip volume
                    onValueChange = { },
                    colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("الانتقال (Transition)", color = Color.Gray, fontSize = 12.sp, fontFamily = CairoFont)
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp)).padding(8.dp)
                ) {
                    Text("Fade In (تلاشي)", color = Color.White, fontSize = 12.sp, fontFamily = CairoFont)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onDuplicate,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تكرار", color = Color.White, fontFamily = CairoFont)
            }
            
            Button(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f), contentColor = Color(0xFFEF4444))
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("حذف", fontFamily = CairoFont)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
        ) {
            Text("حفظ التعديلات الاحترافية", color = DeepSlate, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
        }
    }
}

@Composable
fun AdjustSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.Gray, fontSize = 12.sp, fontFamily = CairoFont)
            Text(String.format("%.2f", value), color = GoldPrimary, fontSize = 12.sp, fontFamily = RobotoMonoFont)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
        )
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(4.dp)
    ) {
        Icon(
            icon, 
            null, 
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.2f), 
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label, 
            color = if (enabled) Color.Gray else Color.Gray.copy(alpha = 0.3f), 
            fontSize = 10.sp, 
            fontFamily = CairoFont
        )
    }
}

fun formatTime(ms: Long): String {
    val sec = ms / 1000
    val min = sec / 60
    return String.format("%02d:%02d", min, sec % 60)
}

