package com.qabas.app

import android.widget.Toast
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.UUID
import kotlin.math.roundToLong
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioProEditorScreen(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    var projectState by remember { mutableStateOf(createBlankEditorProjectState()) }
    val editorPlayer = remember(context) {
        ExoPlayerFactory.createPlayer(context, useCache = false, useAudioFocus = false)
    }
    var previewPlaybackError by remember { mutableStateOf<String?>(null) }
    
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
    var showAnimationPanel by remember { mutableStateOf(false) }
    var showTransitionPicker by remember { mutableStateOf(false) }
    var showAiMagicPanel by remember { mutableStateOf(false) }
    var showTextEditor by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }
    var editingTextClipId by remember { mutableStateOf<String?>(null) }
    var selectedTransitionClipId by remember { mutableStateOf<String?>(null) }
    val selectedClipTrack = projectState.tracks.firstOrNull { track -> track.clips.any { it.id == projectState.selectedClipId } }
    val selectedEditorClip = selectedClipTrack?.clips?.firstOrNull { it.id == projectState.selectedClipId }
    val activeVideoTrack = projectState.tracks.firstOrNull { it.type == TrackType.VIDEO && it.isVisible }
    val activeVideoClip = activeVideoTrack?.clips?.firstOrNull {
        projectState.currentTimeMs >= it.startOffsetMs &&
            projectState.currentTimeMs < it.startOffsetMs + it.durationMs
    }
    val activeOverlayClip = projectState.tracks
        .filter { it.type == TrackType.OVERLAY && it.isVisible }
        .flatMap { it.clips }
        .firstOrNull {
            projectState.currentTimeMs >= it.startOffsetMs &&
                projectState.currentTimeMs < it.startOffsetMs + it.durationMs
        }
    
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
    
    DisposableEffect(editorPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) previewPlaybackError = null
            }

            override fun onPlayerError(error: PlaybackException) {
                previewPlaybackError = error.message ?: "تعذر تشغيل الوسيط"
                editorPlayer.pause()
            }
        }
        editorPlayer.addListener(listener)
        onDispose {
            editorPlayer.removeListener(listener)
            editorPlayer.release()
        }
    }

    LaunchedEffect(activeVideoClip?.id, activeVideoClip?.resourceUri) {
        editorPlayer.pause()
        previewPlaybackError = null
        val clip = activeVideoClip
        if (clip == null || clip.resourceUri.isBlank()) {
            editorPlayer.clearMediaItems()
        } else {
            runCatching {
                editorPlayer.setMediaItem(MediaItem.fromUri(clip.resourceUri))
                editorPlayer.prepare()
                editorPlayer.seekTo((projectState.currentTimeMs - clip.startOffsetMs).coerceAtLeast(0L))
                if (projectState.isPlaying) editorPlayer.play()
            }.onFailure { error ->
                previewPlaybackError = error.message ?: "تعذر فتح ملف الوسائط"
            }
        }
    }

    LaunchedEffect(projectState.currentTimeMs, activeVideoClip?.id, projectState.isPlaying) {
        if (!projectState.isPlaying && activeVideoClip != null) {
            val relativePosition = (projectState.currentTimeMs - activeVideoClip.startOffsetMs).coerceAtLeast(0L)
            if (kotlin.math.abs(editorPlayer.currentPosition - relativePosition) > 80L) {
                editorPlayer.seekTo(relativePosition)
            }
        }
    }

    LaunchedEffect(projectState.isPlaying, activeVideoClip?.id) {
        if (!projectState.isPlaying) {
            editorPlayer.pause()
            return@LaunchedEffect
        }
        editorPlayer.play()
        while (isActive && projectState.isPlaying) {
            kotlinx.coroutines.delay(33L)
            val clip = activeVideoClip
            val nextTimeMs = if (clip != null) {
                val clipEndMs = clip.startOffsetMs + clip.durationMs
                val playerEnded = editorPlayer.playbackState == Player.STATE_ENDED
                if (playerEnded) clipEndMs
                else (clip.startOffsetMs + editorPlayer.currentPosition.coerceAtLeast(0L)).coerceAtMost(clipEndMs)
            } else {
                (projectState.currentTimeMs + 33L).coerceAtMost(projectState.totalDurationMs)
            }
            projectState = projectState.copy(currentTimeMs = nextTimeMs)
            if (nextTimeMs >= projectState.totalDurationMs) {
                editorPlayer.pause()
                projectState = projectState.copy(isPlaying = false)
                break
            }
            if (clip != null && nextTimeMs >= clip.startOffsetMs + clip.durationMs) {
                editorPlayer.pause()
            }
        }
    }

    LaunchedEffect(activeVideoClip?.id, activeVideoClip?.volume, activeVideoTrack?.volume, activeVideoTrack?.isMuted, projectState.tracks.any { it.isSolo }) {
        val track = activeVideoTrack
        val included = track != null && isEditorTrackIncludedInMix(projectState, track)
        editorPlayer.volume = if (included && activeVideoClip != null) {
            (activeVideoClip.volume * track!!.volume).coerceIn(0f, 1f)
        } else 0f
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
                        if (activeVideoClip != null && activeVideoClip.resourceUri.isNotBlank()) {
                            AndroidView(
                                factory = { viewContext ->
                                    TextureView(viewContext).also { textureView ->
                                        editorPlayer.setVideoTextureView(textureView)
                                    }
                                },
                                update = { },
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    activeVideoClip?.let { clip ->
                                        val relativeTime = (projectState.currentTimeMs - clip.startOffsetMs).coerceAtLeast(0L)
                                        val animatedScale = SmartFeaturesEngine.interpolateProperty(
                                            clip.keyframes, relativeTime, "scale", 1f
                                        )
                                        alpha = SmartFeaturesEngine.interpolateProperty(
                                            clip.keyframes, relativeTime, "opacity", 1f
                                        )
                                        scaleX = clip.transform.scaleX * animatedScale
                                        scaleY = clip.transform.scaleY * animatedScale
                                        rotationZ = clip.transform.rotation
                                        translationX = clip.transform.posX
                                        translationY = clip.transform.posY
                                    }
                                }
                            )
                        }
                        if (previewPlaybackError != null) {
                            Text(
                                "تعذر تشغيل المقطع: $previewPlaybackError",
                                color = Color(0xFFFCA5A5),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                            )
                        }
                        
                        if (activeOverlayClip != null && activeOverlayClip.resourceUri.isNotBlank()) {
                            AsyncImage(
                                model = activeOverlayClip.resourceUri,
                                contentDescription = activeOverlayClip.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    val relativeTime = (projectState.currentTimeMs - activeOverlayClip.startOffsetMs).coerceAtLeast(0L)
                                    val animatedScale = SmartFeaturesEngine.interpolateProperty(
                                        activeOverlayClip.keyframes, relativeTime, "scale", 1f
                                    )
                                    alpha = SmartFeaturesEngine.interpolateProperty(
                                        activeOverlayClip.keyframes, relativeTime, "opacity", 1f
                                    )
                                    scaleX = activeOverlayClip.transform.scaleX * animatedScale
                                    scaleY = activeOverlayClip.transform.scaleY * animatedScale
                                    rotationZ = activeOverlayClip.transform.rotation
                                    translationX = activeOverlayClip.transform.posX
                                    translationY = activeOverlayClip.transform.posY
                                }
                            )
                        }

                        // Active Text Layer or Kinetic Captions
                        val activeTextClip = projectState.tracks.firstOrNull { it.type == TrackType.TEXT && it.isVisible }?.clips?.firstOrNull { 
                            projectState.currentTimeMs >= it.startOffsetMs && projectState.currentTimeMs < it.startOffsetMs + it.durationMs
                        }
                        
                        if (activeVideoClip == null && activeTextClip == null) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "لا توجد وسائط عند موضع التشغيل الحالي",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                TextButton(onClick = { showMediaBrowser = true }) {
                                    Text("استيراد وسائط", color = GoldPrimary, fontFamily = CairoFont)
                                }
                            }
                        }

                        if (activeTextClip != null) {
                            val currentScale = SmartFeaturesEngine.interpolateProperty(
                                activeTextClip.keyframes,
                                projectState.currentTimeMs - activeTextClip.startOffsetMs,
                                "scale",
                                1f
                            )
                            val currentOpacity = SmartFeaturesEngine.interpolateProperty(
                                activeTextClip.keyframes,
                                projectState.currentTimeMs - activeTextClip.startOffsetMs,
                                "opacity",
                                1f
                            )

                            Text(
                                activeTextClip.title,
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
                                val clip = selectedEditorClip
                                if (clip != null) {
                                    val relativeTime = projectState.currentTimeMs - clip.startOffsetMs
                                    val scale = SmartFeaturesEngine.interpolateProperty(clip.keyframes, relativeTime, "scale", 1f)
                                    val opacity = SmartFeaturesEngine.interpolateProperty(clip.keyframes, relativeTime, "opacity", 1f)
                                    val updated = addEditorKeyframesAtPlayhead(
                                        projectState,
                                        clip.id,
                                        mapOf("scale" to scale, "opacity" to opacity)
                                    )
                                    if (updated != null && updated != projectState) {
                                        saveToHistory()
                                        projectState = updated
                                        Toast.makeText(context, "تم تسجيل إطاري الحركة عند موضع التشغيل", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = selectedEditorClip != null &&
                                selectedEditorClip.type != TrackType.AUDIO &&
                                selectedClipTrack?.isLocked != true,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape).border(1.dp, GoldPrimary, CircleShape)
                        ) {
                            Icon(Icons.Default.AddLocation, null, tint = GoldPrimary)
                        }
                    }
                }

                // Playback Controls Overlay
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            findAdjacentEditorClip(projectState, forward = false)?.let { clip ->
                                projectState = projectState.copy(
                                    currentTimeMs = clip.startOffsetMs,
                                    selectedClipId = clip.id,
                                    isPlaying = false
                                )
                            }
                        },
                        enabled = findAdjacentEditorClip(projectState, forward = false) != null
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "المقطع السابق",
                            tint = if (findAdjacentEditorClip(projectState, forward = false) != null) Color.White else Color.Gray
                        )
                    }
                    
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
                    
                    IconButton(
                        onClick = {
                            findAdjacentEditorClip(projectState, forward = true)?.let { clip ->
                                projectState = projectState.copy(
                                    currentTimeMs = clip.startOffsetMs,
                                    selectedClipId = clip.id,
                                    isPlaying = false
                                )
                            }
                        },
                        enabled = findAdjacentEditorClip(projectState, forward = true) != null
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "المقطع التالي",
                            tint = if (findAdjacentEditorClip(projectState, forward = true) != null) Color.White else Color.Gray
                        )
                    }
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
                                        if (!track.isLocked) projectState = projectState.copy(selectedClipId = clipId)
                                    },
                                    onClipDragStarted = { saveToHistory() },
                                    onClipMove = { clipId, startMs ->
                                        moveEditorClip(projectState, clipId, startMs)?.let { projectState = it }
                                    },
                                    onClipTrimStart = { clipId, startMs ->
                                        trimEditorClipStart(projectState, clipId, startMs)?.let { projectState = it }
                                    },
                                    onClipTrimEnd = { clipId, endMs ->
                                        trimEditorClipEnd(projectState, clipId, endMs)?.let { projectState = it }
                                    },
                                    onTransitionClick = { clipId ->
                                        if (!track.isLocked) {
                                            selectedTransitionClipId = clipId
                                            showTransitionPicker = true
                                        }
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
                            val clipToSplit = selectedEditorClip
                            if (clipToSplit != null) {
                                val updated = splitEditorClip(projectState, clipToSplit.id)
                                if (updated != null) {
                                    saveToHistory()
                                    projectState = updated
                                    Toast.makeText(context, "تم تقسيم المقطع مع الحفاظ على إطارات الحركة ✂️", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "ضع مؤشر التشغيل داخل المقطع بعيداً عن حافتيه", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = projectState.selectedClipId != null && selectedClipTrack?.isLocked != true
                    )
                    ToolButton(Icons.Default.Audiotrack, "صوتيات", onClick = { projectState = projectState.copy(workspaceMode = WorkspaceMode.AUDIO) })
                    ToolButton(
                        Icons.Default.TextFields,
                        "نصوص",
                        onClick = {
                            val selectedText = selectedEditorClip?.takeIf { it.type == TrackType.TEXT }
                            if (selectedText != null && selectedClipTrack?.isLocked == true) {
                                Toast.makeText(context, "المسار مقفل؛ ألغِ القفل أولاً", Toast.LENGTH_SHORT).show()
                            } else {
                                editingTextClipId = selectedText?.id
                                textDraft = selectedText?.title.orEmpty()
                                showTextEditor = true
                            }
                        }
                    )
                    ToolButton(Icons.Default.AutoAwesome, "تأثيرات", onClick = { showAdjustPanel = true }, enabled = selectedEditorClip != null && selectedClipTrack?.isLocked != true)
                    ToolButton(Icons.Default.Animation, "تحريك", onClick = { showAnimationPanel = true }, enabled = selectedEditorClip != null && selectedEditorClip.type != TrackType.AUDIO && selectedClipTrack?.isLocked != true)
                    ToolButton(Icons.Default.AutoFixHigh, "سحر AI", onClick = { showAiMagicPanel = true })
                    ToolButton(Icons.Default.ContentCopy, "ملصقات", onClick = { showMediaBrowser = true })
                    ToolButton(Icons.Default.Tune, "ضبط", onClick = { showAdjustPanel = true }, enabled = projectState.selectedClipId != null && selectedClipTrack?.isLocked != true)
                }
            }
        }
    }

    if (showTextEditor) {
        AlertDialog(
            onDismissRequest = {
                showTextEditor = false
                editingTextClipId = null
            },
            containerColor = Color(0xFF1A1A1A),
            title = {
                Text(
                    if (editingTextClipId == null) "إضافة نص إلى الفيديو" else "تحرير النص",
                    color = Color.White,
                    fontFamily = CairoFont
                )
            },
            text = {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("محتوى النص") },
                    minLines = 2,
                    maxLines = 4,
                    singleLine = false
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val content = textDraft.trim()
                        if (content.isNotEmpty()) {
                            val updated = if (editingTextClipId != null) {
                                updateEditorTextClip(projectState, editingTextClipId!!, content)
                            } else {
                                addEditorTextClip(projectState, content)
                            }
                            if (updated != null && updated != projectState) {
                                saveToHistory()
                                projectState = updated
                            }
                        }
                        showTextEditor = false
                        editingTextClipId = null
                    }
                ) {
                    Text("حفظ النص", color = GoldPrimary, fontFamily = CairoFont)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTextEditor = false
                        editingTextClipId = null
                    }
                ) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            }
        )
    }

    if (showAnimationPanel && selectedEditorClip != null) {
        ModalBottomSheet(
            onDismissRequest = { showAnimationPanel = false },
            containerColor = Color(0xFF1A1A1A)
        ) {
            AnimationWorkspace(
                clip = selectedEditorClip,
                onTransformChanged = { transform ->
                    val clipId = selectedEditorClip.id
                    projectState = projectState.copy(
                        tracks = projectState.tracks.map { track ->
                            if (track.id == selectedClipTrack?.id) {
                                track.copy(clips = track.clips.map { clip ->
                                    if (clip.id == clipId) clip.copy(transform = transform) else clip
                                })
                            } else track
                        }
                    )
                },
                onEditStarted = { saveToHistory() }
            )
        }
    }

    // AI tools remain informational until real transcription and background-removal services are connected.
    if (showAiMagicPanel) {
        ModalBottomSheet(
            onDismissRequest = { showAiMagicPanel = false },
            containerColor = Color(0xFF0F0F0F)
        ) {
            AiMagicPanel()
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
            AudioMixerWorkspace(
                state = projectState,
                onTracksUpdated = { updatedTracks ->
                    projectState = projectState.copy(tracks = updatedTracks)
                },
                onEditStarted = { saveToHistory() }
            )
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
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = {
                                saveToHistory()
                                projectState = projectState.copy(tracks = projectState.tracks.map {
                                    if (it.id == track.id) it.copy(isVisible = !it.isVisible) else it
                                })
                            }) {
                                Icon(
                                    if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (track.isVisible) "إخفاء " + track.name else "إظهار " + track.name,
                                    tint = if (track.isVisible) GoldPrimary else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = {
                                saveToHistory()
                                projectState = projectState.copy(tracks = projectState.tracks.map {
                                    if (it.id == track.id) it.copy(isMuted = !it.isMuted) else it
                                })
                            }) {
                                Icon(
                                    if (track.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (track.isMuted) "إلغاء كتم " + track.name else "كتم " + track.name,
                                    tint = if (track.isMuted) Color.Gray else GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = {
                                saveToHistory()
                                projectState = projectState.copy(
                                    tracks = projectState.tracks.map {
                                        if (it.id == track.id) it.copy(isLocked = !it.isLocked) else it
                                    },
                                    selectedClipId = if (!track.isLocked && selectedClipTrack?.id == track.id) null else projectState.selectedClipId
                                )
                            }) {
                                Icon(
                                    if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (track.isLocked) "إلغاء قفل " + track.name else "قفل " + track.name,
                                    tint = if (track.isLocked) Color(0xFFF59E0B) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
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
                transform = currentClip?.transform ?: TransformParams(),
                speed = currentClip?.speed ?: 1f,
                volume = currentClip?.volume ?: 1f,
                onEditStarted = { saveToHistory() },
                onFiltersChanged = { newFilters ->
                    projectState = projectState.copy(
                        tracks = projectState.tracks.map { track ->
                            track.copy(clips = track.clips.map { clip ->
                                if (clip.id == projectState.selectedClipId) clip.copy(filters = newFilters) else clip
                            })
                        }
                    )
                },
                onTransformChanged = { newTransform ->
                    projectState = projectState.copy(
                        tracks = projectState.tracks.map { track ->
                            track.copy(clips = track.clips.map { clip ->
                                if (clip.id == projectState.selectedClipId) clip.copy(transform = newTransform) else clip
                            })
                        }
                    )
                },
                onSpeedChanged = { newSpeed ->
                    projectState = projectState.copy(
                        tracks = projectState.tracks.map { track ->
                            track.copy(clips = track.clips.map { clip ->
                                if (clip.id == projectState.selectedClipId) clip.copy(speed = newSpeed.coerceIn(0.1f, 5f)) else clip
                            })
                        }
                    )
                },
                onVolumeChanged = { newVolume ->
                    projectState = projectState.copy(
                        tracks = projectState.tracks.map { track ->
                            track.copy(clips = track.clips.map { clip ->
                                if (clip.id == projectState.selectedClipId) clip.copy(volume = newVolume.coerceIn(0f, 1f)) else clip
                            })
                        }
                    )
                },
                onDelete = {
                    val clipId = projectState.selectedClipId
                    val updated = clipId?.let { deleteEditorClip(projectState, it) }
                    if (updated != null) {
                        saveToHistory()
                        projectState = updated
                        Toast.makeText(
                            context,
                            if (projectState.isMagneticTimeline) "تم حذف المقطع وإغلاق الفجوة في مساره" else "تم حذف المقطع",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    showAdjustPanel = false
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
                    scope.launch {
                        val durationMs = resolveEditorAssetDurationMs(context, asset)
                        if (durationMs == null || durationMs <= 0L) {
                            Toast.makeText(
                                context,
                                "تعذر قراءة مدة الملف؛ لم تتم إضافته إلى المشروع",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        val trackType = when (asset.type) {
                            "video" -> TrackType.VIDEO
                            "audio" -> TrackType.AUDIO
                            else -> TrackType.OVERLAY
                        }
                        val updated = addEditorMediaClip(
                            state = projectState,
                            title = asset.title,
                            resourceUri = asset.url,
                            type = trackType,
                            durationMs = durationMs
                        )
                        if (updated == null) {
                            Toast.makeText(context, "تعذر إضافة هذا الملف إلى المسار", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        saveToHistory()
                        projectState = updated
                        showMediaBrowser = false
                        Toast.makeText(context, "تمت إضافة الوسيط بمدته المقروءة إلى الخط الزمني", Toast.LENGTH_SHORT).show()
                    }
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
    onClipDragStarted: () -> Unit = {},
    onClipMove: (String, Long) -> Unit = { _, _ -> },
    onClipTrimStart: (String, Long) -> Unit = { _, _ -> },
    onClipTrimEnd: (String, Long) -> Unit = { _, _ -> },
    onTransitionClick: (String) -> Unit = {}
) {
    val density = LocalDensity.current.density
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
                key(clip.id) {
                val isSelected = clip.id == selectedClipId
                var moveOriginMs by remember(clip.id) { mutableStateOf(clip.startOffsetMs) }
                var moveDeltaMs by remember(clip.id) { mutableStateOf(0L) }
                var trimOriginMs by remember(clip.id) { mutableStateOf(clip.startOffsetMs) }
                var trimDeltaMs by remember(clip.id) { mutableStateOf(0L) }
                Surface(
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .width((clip.durationMs.toFloat() / 10f * zoom).dp)
                        .offset(x = (clip.startOffsetMs.toFloat() / 10f * zoom).dp)
                        .align(Alignment.CenterStart)
                        .clickable { onClipClick(clip.id) }
                        .pointerInput(clip.id, zoom, track.isLocked) {
                            detectDragGestures(
                                onDragStart = {
                                    if (!track.isLocked) {
                                        onClipClick(clip.id)
                                        onClipDragStarted()
                                        moveOriginMs = clip.startOffsetMs
                                        moveDeltaMs = 0L
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (!track.isLocked) {
                                        moveDeltaMs += (dragAmount.x / density / zoom * 10f).roundToLong()
                                        onClipMove(clip.id, moveOriginMs + moveDeltaMs)
                                    }
                                }
                            )
                        },
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

                        if (isSelected && !track.isLocked) {
                            Box(
                                modifier = Modifier.align(Alignment.CenterStart)
                                    .width(8.dp).fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.85f))
                                    .pointerInput(clip.id, zoom) {
                                        detectDragGestures(
                                            onDragStart = {
                                                onClipDragStarted()
                                                trimOriginMs = clip.startOffsetMs
                                                trimDeltaMs = 0L
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                trimDeltaMs += (dragAmount.x / density / zoom * 10f).roundToLong()
                                                onClipTrimStart(clip.id, trimOriginMs + trimDeltaMs)
                                            }
                                        )
                                    }
                            )
                            Box(
                                modifier = Modifier.align(Alignment.CenterEnd)
                                    .width(8.dp).fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.85f))
                                    .pointerInput(clip.id, zoom) {
                                        detectDragGestures(
                                            onDragStart = {
                                                onClipDragStarted()
                                                trimOriginMs = clip.startOffsetMs + clip.durationMs
                                                trimDeltaMs = 0L
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                trimDeltaMs += (dragAmount.x / density / zoom * 10f).roundToLong()
                                                onClipTrimEnd(clip.id, trimOriginMs + trimDeltaMs)
                                            }
                                        )
                                    }
                            )
                        }

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
}

@Composable
fun AnimationWorkspace(
    clip: EditorClip,
    onTransformChanged: (TransformParams) -> Unit,
    onEditStarted: () -> Unit
) {
    var editingControl by remember { mutableStateOf<String?>(null) }

    fun beginEdit(control: String) {
        if (editingControl != control) {
            onEditStarted()
            editingControl = control
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("تحريك وتحويل المقطع", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 20.sp)
        Text("تُطبّق القيم مباشرة على معاينة المقطع المحدد.", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("الحجم", color = Color.White, fontFamily = CairoFont)
            Text(String.format("%.2f×", clip.transform.scaleX), color = GoldPrimary, fontFamily = RobotoMonoFont)
        }
        Slider(
            value = clip.transform.scaleX.coerceIn(0.25f, 3f),
            onValueChange = { value ->
                beginEdit("scale")
                onTransformChanged(clip.transform.copy(scaleX = value, scaleY = value))
            },
            onValueChangeFinished = { editingControl = null },
            valueRange = 0.25f..3f
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("الدوران", color = Color.White, fontFamily = CairoFont)
            Text("${clip.transform.rotation.toInt()}°", color = GoldPrimary, fontFamily = RobotoMonoFont)
        }
        Slider(
            value = clip.transform.rotation.coerceIn(-180f, 180f),
            onValueChange = { value ->
                beginEdit("rotation")
                onTransformChanged(clip.transform.copy(rotation = value))
            },
            onValueChangeFinished = { editingControl = null },
            valueRange = -180f..180f
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("الموضع الأفقي", color = Color.White, fontFamily = CairoFont)
            Text("${clip.transform.posX.toInt()} px", color = GoldPrimary, fontFamily = RobotoMonoFont)
        }
        Slider(
            value = clip.transform.posX.coerceIn(-500f, 500f),
            onValueChange = { value ->
                beginEdit("position-x")
                onTransformChanged(clip.transform.copy(posX = value))
            },
            onValueChangeFinished = { editingControl = null },
            valueRange = -500f..500f
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("الموضع العمودي", color = Color.White, fontFamily = CairoFont)
            Text("${clip.transform.posY.toInt()} px", color = GoldPrimary, fontFamily = RobotoMonoFont)
        }
        Slider(
            value = clip.transform.posY.coerceIn(-500f, 500f),
            onValueChange = { value ->
                beginEdit("position-y")
                onTransformChanged(clip.transform.copy(posY = value))
            },
            onValueChangeFinished = { editingControl = null },
            valueRange = -500f..500f
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AiMagicPanel() {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            "أدوات الذكاء الاصطناعي",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontFamily = CairoFont,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "التفريغ الصوتي وإزالة الخلفية وتحسين الصوت غير متاحة في هذا المحرر حالياً؛ لم يتم ربط خدمات تنفيذ فعلية لهذه العمليات.",
            color = Color.Gray,
            fontFamily = CairoFont,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
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
fun AudioMixerWorkspace(
    state: EditorProjectState,
    onTracksUpdated: (List<EditorTrack>) -> Unit,
    onEditStarted: () -> Unit = {}
) {
    var editingTrackId by remember { mutableStateOf<String?>(null) }
    val audioTracks = state.tracks.filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("ميكسر الصوت الاحترافي (Audio Mixer)", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = CairoFont, fontSize = 20.sp)
        Text(
            "إعدادات مستوى المسارات والكتم والعزل تُحفظ مع المشروع",
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = CairoFont
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (audioTracks.isEmpty()) {
            Text("أضف مسار فيديو أو صوتاً لبدء ضبط مستويات الصوت.", color = Color.Gray, fontFamily = CairoFont)
        } else {
            audioTracks.forEach { track ->
                val effectivelyAudible = isEditorTrackIncludedInMix(state, track)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(track.name, color = Color.White, fontFamily = CairoFont)
                        Text("${(track.volume * 100).toInt()}%", color = GoldPrimary, fontSize = 12.sp)
                    }
                    Slider(
                        value = track.volume.coerceIn(0f, 1f),
                        onValueChange = { value ->
                            if (editingTrackId != track.id) {
                                onEditStarted()
                                editingTrackId = track.id
                            }
                            onTracksUpdated(state.tracks.map {
                                if (it.id == track.id) it.copy(volume = value.coerceIn(0f, 1f)) else it
                            })
                        },
                        onValueChangeFinished = { editingTrackId = null },
                        enabled = !track.isMuted,
                        colors = SliderDefaults.colors(
                            thumbColor = GoldPrimary,
                            activeTrackColor = GoldPrimary,
                            disabledThumbColor = Color.Gray,
                            disabledActiveTrackColor = Color.Gray
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(
                            if (track.isMuted) "إلغاء الكتم" else "كتم",
                            color = if (track.isMuted) GoldPrimary else Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                onEditStarted()
                                onTracksUpdated(state.tracks.map {
                                    if (it.id == track.id) it.copy(isMuted = !it.isMuted) else it
                                })
                            }
                        )
                        Text(
                            if (track.isSolo) "إلغاء العزل" else "عزل المسار",
                            color = if (track.isSolo) GoldPrimary else Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                onEditStarted()
                                onTracksUpdated(state.tracks.map {
                                    if (it.id == track.id) it.copy(isSolo = !it.isSolo) else it
                                })
                            }
                        )
                        Text(
                            if (effectivelyAudible) "مفعّل ضمن المزيج" else "مستبعد من المزيج",
                            color = if (effectivelyAudible) Color(0xFF10B981) else Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "ملاحظة: هذه الإعدادات تُحفظ في المشروع؛ تشغيل ومزج الصوت الفعلي يحتاج إلى محرك صوت متصل.",
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = CairoFont
        )
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
                        durationSeconds = 0,
                        type = "video"
                    )
                }
                val centralVideos = centralAssets.filter { it.type == CentralMediaType.VIDEO }.map { c ->
                    MediaAsset(
                        id = c.id,
                        title = c.title,
                        url = c.displayUrl,
                        thumbnailUrl = "",
                        durationSeconds = 0,
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
                        durationSeconds = 0,
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
                        durationSeconds = if (c.type == CentralMediaType.IMAGE) 5 else 0,
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
                        durationSeconds = 0,
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
                            Text(if (asset.durationSeconds > 0) "${asset.durationSeconds}s" else "تُقرأ عند الإضافة", color = Color.Gray, fontSize = 8.sp, fontFamily = NotoSansFont)
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
    transform: TransformParams = TransformParams(),
    speed: Float = 1f,
    volume: Float = 1f,
    onEditStarted: () -> Unit = {},
    onFiltersChanged: (FilterParams) -> Unit,
    onTransformChanged: (TransformParams) -> Unit = {},
    onSpeedChanged: (Float) -> Unit = {},
    onVolumeChanged: (Float) -> Unit = {},
    onDelete: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("الألوان") }
    var historyCaptured by remember { mutableStateOf(false) }
    fun beginEdit() {
        if (!historyCaptured) {
            onEditStarted()
            historyCaptured = true
        }
    }
    fun finishEdit() {
        historyCaptured = false
    }
    
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
                AdjustSlider("السطوع (Brightness)", filters.brightness, 0f..2f, onValueChange = { onFiltersChanged(filters.copy(brightness = it)) }, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)
                AdjustSlider("التباين (Contrast)", filters.contrast, 0f..2f, onValueChange = { onFiltersChanged(filters.copy(contrast = it)) }, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)
                AdjustSlider("التشبع (Saturation)", filters.saturation, 0f..2f, onValueChange = { onFiltersChanged(filters.copy(saturation = it)) }, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)
                AdjustSlider("الحدة (Sharpness)", filters.sharpness, 0f..1f, onValueChange = { onFiltersChanged(filters.copy(sharpness = it)) }, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)
            }
            "التحويل" -> {
                Text("الأبعاد والموقع (Desktop Precision)", color = GoldPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                Spacer(modifier = Modifier.height(12.dp))
                AdjustSlider("الحجم الأفقي", transform.scaleX, 0.1f..3f, onValueChange = { onTransformChanged(transform.copy(scaleX = it)) }, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)
                AdjustSlider("الحجم العمودي", transform.scaleY, 0.1f..3f, onValueChange = { onTransformChanged(transform.copy(scaleY = it)) }, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)
                AdjustSlider("الدوران", transform.rotation, -180f..180f, onValueChange = { onTransformChanged(transform.copy(rotation = it)) }, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)
                AdjustSlider("الموضع الأفقي", transform.posX, -500f..500f, onValueChange = { onTransformChanged(transform.copy(posX = it)) }, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)
                AdjustSlider("الموضع العمودي", transform.posY, -500f..500f, onValueChange = { onTransformChanged(transform.copy(posY = it)) }, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)

                Spacer(modifier = Modifier.height(12.dp))
                Text("السرعة (Speed)", color = GoldPrimary, fontSize = 12.sp, fontFamily = CairoFont)
                AdjustSlider("سرعة المقطع", speed, 0.1f..5f, onValueChange = onSpeedChanged, onEditStarted = ::beginEdit, onEditFinished = ::finishEdit)
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
                    value = volume.coerceIn(0f, 1f),
                    onValueChange = {
                        beginEdit()
                        onVolumeChanged(it)
                    },
                    onValueChangeFinished = ::finishEdit,
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
fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onEditStarted: () -> Unit = {},
    onEditFinished: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.Gray, fontSize = 12.sp, fontFamily = CairoFont)
            Text(String.format("%.2f", value), color = GoldPrimary, fontSize = 12.sp, fontFamily = RobotoMonoFont)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                onEditStarted()
                onValueChange(it)
            },
            onValueChangeFinished = onEditFinished,
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

private suspend fun resolveEditorAssetDurationMs(context: android.content.Context, asset: MediaAsset): Long? {
    if (asset.type == "image") return 5000L
    return withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val parsedUri = Uri.parse(asset.url)
            if (parsedUri.scheme == "http" || parsedUri.scheme == "https") {
                retriever.setDataSource(asset.url, emptyMap())
            } else {
                retriever.setDataSource(context, parsedUri)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

fun formatTime(ms: Long): String {
    val sec = ms / 1000
    val min = sec / 60
    return String.format("%02d:%02d", min, sec % 60)
}

