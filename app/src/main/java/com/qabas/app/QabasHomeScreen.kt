package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CutCornerShape
import com.qabas.app.ui.components.GlassmorphicSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentProjects: List<ProjectService.Project>,
    onNewProject: (String?) -> Unit,
    onViewProjects: () -> Unit,
    onViewSettings: () -> Unit,
    onViewProfile: () -> Unit = {},
    onViewNotifications: () -> Unit = {},
    onOpenProject: (ProjectService.Project) -> Unit,
    onViewApiDocs: () -> Unit = {},
    onTeleprompter: () -> Unit = {},
    onAudioLibrary: () -> Unit = {},
    onAiAssistant: () -> Unit = {},
    onYouTubeStudio: () -> Unit = {},
    onDeveloperDashboard: () -> Unit = {},
    onLeaderboard: () -> Unit = {},
    onNavigateToTasteProfile: () -> Unit = {},
    onStudioGateway: () -> Unit = {},
    onPremiumUpgrade: () -> Unit = {},
    onRewards: () -> Unit = {},
    onKnowledgeHub: () -> Unit = {},
    onContentGuard: () -> Unit = {},
    onQuranHub: () -> Unit = {},
    onHadithStudio: () -> Unit = {},
    onVideoStyleCloner: () -> Unit = {},
    onPhotoStudio: () -> Unit = {},
    onStyleSelection: () -> Unit = {},
    onIslamicLibrary: () -> Unit = {},
    onAzkar: () -> Unit = {},
    onPrayerTimes: () -> Unit = {},
    onScholarBiographies: () -> Unit = {},
    onProEditor: () -> Unit = {},
    onMoneyPrinterTurbo: () -> Unit = {},
    onVoiceStudio: () -> Unit = {},
    onMediaBrowser: () -> Unit = {},
    onReels: () -> Unit = {},
    onRestoreWorkspaceDraft: (() -> Unit)? = null,
    onRules: () -> Unit = {},
    onAiStudioAgent: () -> Unit = {},
    onViewDownloads: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val homeStyle = HomeScreenStyleManager.currentStyle.collectAsState().value
    if (homeStyle == HomeScreenStyle.SPATIAL_CYBER_HUD) {
        SpatialCommandHomeScreen(
            recentProjects = recentProjects,
            onNewProject = onNewProject,
            onViewProjects = onViewProjects,
            onViewSettings = onViewSettings,
            onViewProfile = onViewProfile,
            onViewNotifications = onViewNotifications,
            onOpenProject = onOpenProject,
            onViewApiDocs = onViewApiDocs,
            onTeleprompter = onTeleprompter,
            onAudioLibrary = onAudioLibrary,
            onAiAssistant = onAiAssistant,
            onYouTubeStudio = onYouTubeStudio,
            onDeveloperDashboard = onDeveloperDashboard,
            onLeaderboard = onLeaderboard,
            onNavigateToTasteProfile = onNavigateToTasteProfile,
            onStudioGateway = onStudioGateway,
            onPremiumUpgrade = onPremiumUpgrade,
            onRewards = onRewards,
            onKnowledgeHub = onKnowledgeHub,
            onContentGuard = onContentGuard,
            onQuranHub = onQuranHub,
            onHadithStudio = onHadithStudio,
            onVideoStyleCloner = onVideoStyleCloner,
            onPhotoStudio = onPhotoStudio,
            onStyleSelection = onStyleSelection,
            onIslamicLibrary = onIslamicLibrary,
            onAzkar = onAzkar,
            onPrayerTimes = onPrayerTimes,
            onScholarBiographies = onScholarBiographies,
            onProEditor = onProEditor,
            onMoneyPrinterTurbo = onMoneyPrinterTurbo,
            onVoiceStudio = onVoiceStudio,
            onMediaBrowser = onMediaBrowser,
            onReels = onReels,
            onRestoreWorkspaceDraft = onRestoreWorkspaceDraft,
            onRules = onRules,
            onAiStudioAgent = onAiStudioAgent,
            onViewDownloads = onViewDownloads,
            bottomBar = bottomBar
        )
        return
    }

    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("qabas_prefs", android.content.Context.MODE_PRIVATE)
    }
    val isAdmin = prefs.getBoolean("is_admin", false)
    val isDeveloper = remember(context) { AdminGuard.isDashboardAccessAllowed(context) }
    val networkStatus by UserPreferencesManager.networkStatus.collectAsState()
    val autoSaveStatus by WorkspaceAutoSaveManager.status.collectAsState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentAudioTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isAudioPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Quick prompt & voice input states
    var quickPromptText by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var isAnalyzingVoice by remember { mutableStateOf(false) }
    val recorderHelper = remember { AudioRecorderHelper(context) }

    // Sector Selection (0: Studios, 1: Islamic Suite, 2: AI Engines, 3: Projects)
    var selectedSector by remember { mutableIntStateOf(0) }

    val activeAiEngine = remember { AiModelLatencyRouter.getEffectiveEngine() }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val ok = recorderHelper.startRecording()
            if (ok) {
                isRecordingVoice = true
                Toast.makeText(context, Translator.tr("بدأ التسجيل... تحدث بفكرتك الدعوية 🎙️"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, Translator.tr("تعذر بدء الميكروفون"), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, Translator.tr("يلزم السماح بالميكروفون للتسجيل الصوتي"), Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorderHelper.release() }
    }

    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            showExitDialog = true
        }
    }

    if (showPlaylistSheet) {
        AudioPlaylistBottomSheet(onDismiss = { showPlaylistSheet = false })
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    Translator.tr("تأكيد الخروج"),
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    Translator.tr("هل أنت متأكد أنك تريد إغلاق تطبيق قبس؟"),
                    color = Color.White,
                    fontFamily = CairoFont
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    (context as? android.app.Activity)?.finishAffinity()
                }) {
                    Text(Translator.tr("نعم"), color = Color(0xFFEF4444), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(Translator.tr("إلغاء"), color = Color.White, fontFamily = CairoFont)
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DeepSlate,
                drawerContentColor = Color.White,
                modifier = Modifier.widthIn(max = 330.dp)
            ) {
                QabasNavigationDrawerContent(
                    isAdmin = isAdmin,
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onNavigateTo = { action ->
                        scope.launch {
                            drawerState.close()
                            action()
                        }
                    },
                    onStudioGateway = onStudioGateway,
                    onProEditor = onProEditor,
                    onMoneyPrinterTurbo = onMoneyPrinterTurbo,
                    onVoiceStudio = onVoiceStudio,
                    onPhotoStudio = onPhotoStudio,
                    onTeleprompter = onTeleprompter,
                    onVideoStyleCloner = onVideoStyleCloner,
                    onYouTubeStudio = onYouTubeStudio,
                    onViewProjects = onViewProjects,
                    onQuranHub = onQuranHub,
                    onIslamicLibrary = onIslamicLibrary,
                    onAzkar = onAzkar,
                    onScholarBiographies = onScholarBiographies,
                    onPrayerTimes = onPrayerTimes,
                    onAudioLibrary = onAudioLibrary,
                    onAiAssistant = onAiAssistant,
                    onContentGuard = onContentGuard,
                    onLeaderboard = onLeaderboard,
                    onNavigateToTasteProfile = onNavigateToTasteProfile,
                    onPremiumUpgrade = onPremiumUpgrade,
                    onViewProfile = onViewProfile,
                    onViewSettings = onViewSettings,
                    onViewApiDocs = onViewApiDocs,
                    onDeveloperDashboard = onDeveloperDashboard,
                    onNavigateToRules = onRules,
                    onAiStudioAgent = onAiStudioAgent,
                    onViewDownloads = onViewDownloads
                )
            }
        }
    ) {
        Scaffold(
            containerColor = DeepSlate,
            bottomBar = {
                Column {
                    // Floating Mini Player Bar
                    AnimatedVisibility(
                        visible = currentAudioTrack != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        currentAudioTrack?.let { track ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable { showPlaylistSheet = true },
                                shape = RoundedCornerShape(16.dp),
                                color = CardSurface.copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(GoldPrimary, AiCyan))),
                                shadowElevation = 12.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(GoldPrimary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                                        Text(track.artist, color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 10.sp, maxLines = 1)
                                    }
                                    val homeAudioPlayTint by animateColorAsState(
                                        targetValue = if (isAudioPlaying) Color(0xFF10B981) else GoldPrimary,
                                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                                        label = "home_audio_play_tint"
                                    )
                                    IconButton(
                                        onClick = { AudioPlaybackManager.togglePlayState(context) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = homeAudioPlayTint)
                                    }
                                }
                            }
                        }
                    }
                    bottomBar()
                }
            },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = Translator.tr("القائمة الجانبية"),
                                tint = GoldPrimary
                            )
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Holographic Miniature Animated Logo
                            CyberneticAnimatedLogo(
                                size = 32.dp,
                                showOrbitalHud = false,
                                showCosmicGlow = false
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "قَبَس",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = AiCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, AiCyan.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "HUD 2.0",
                                            color = AiCyanLight,
                                            fontFamily = NotoSansFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "استوديو الإنتاج الفضائي الكوانتي",
                                    color = Color(0xFF64748B),
                                    fontFamily = NotoSansFont,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    },
                    actions = {
                        // مؤشر حالة الشبكة
                        val (netIcon, netColor) = when (networkStatus) {
                            ConnectivityObserver.Status.Available -> Icons.Default.Wifi to Color(0xFF10B981)
                            else -> Icons.Default.WifiOff to Color(0xFFEF4444)
                        }
                        IconButton(onClick = { 
                            Toast.makeText(context, if (networkStatus == ConnectivityObserver.Status.Available) "متصل بالإنترنت" else "أنت الآن في وضع عدم الاتصال", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(netIcon, contentDescription = null, tint = netColor.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        }

                        // AI Engine Latency HUD pill
                        Surface(
                            onClick = {
                                if (isAdmin) onDeveloperDashboard() else onViewApiDocs()
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, if (activeAiEngine == "Groq") Color(0xFF10B981).copy(alpha = 0.5f) else GoldPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (activeAiEngine == "Groq") Color(0xFF10B981) else GoldPrimary)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (activeAiEngine == "Groq") "Groq ⚡" else "Gemini 💎",
                                    color = Color.White,
                                    fontFamily = NotoSansFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onViewDownloads,
                            modifier = Modifier.testTag("home_topbar_downloads_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "مركز التنزيلات", tint = Color(0xFF00E5FF))
                        }

                        IconButton(onClick = onViewNotifications) {
                            Icon(Icons.Default.NotificationsNone, contentDescription = "الإشعارات", tint = Color.White)
                        }

                        IconButton(onClick = onViewProfile) {
                            Surface(
                                modifier = Modifier.size(30.dp),
                                shape = CircleShape,
                                color = CardSurface,
                                border = BorderStroke(1.dp, GoldPrimary)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = "الملف الشخصي", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 0. شريط النصائح والحكم الذكي المقسم (قرآن، سنة، مطور)
                QabasSmartTipsBar(
                    isDeveloper = isDeveloper,
                    onNavigateToDeveloperDashboard = onDeveloperDashboard
                )

                // 1. Quantum Command Deck (Hero Section)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    CardSurface.copy(alpha = 0.95f),
                                    DeepSlate.copy(alpha = 0.98f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    GoldPrimary.copy(alpha = 0.45f),
                                    AiCyan.copy(alpha = 0.25f),
                                    GoldPrimary.copy(alpha = 0.45f)
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Spiritual Verse Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "« ادْعُ إِلَىٰ سَبِيلِ رَبِّكَ بِالْحِكْمَةِ وَالْمَوْعِظَةِ الْحَسَنَةِ »",
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "نواة الإنتاج",
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Direct Spatial Prompt & Voice Input Bar
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DeepSlate.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = quickPromptText,
                                        onValueChange = { quickPromptText = it },
                                        placeholder = {
                                            Text(
                                                text = if (isRecordingVoice) "جاري الاستماع لصوتك..." else "اكتب فكرة مقطعك، أو آية، أو سيناريو...",
                                                color = Color(0xFF64748B),
                                                fontFamily = CairoFont,
                                                fontSize = 12.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        singleLine = true
                                    )

                                    // Voice Recording Trigger
                                    IconButton(
                                        onClick = {
                                            if (isRecordingVoice) {
                                                isRecordingVoice = false
                                                scope.launch {
                                                    val path = recorderHelper.stopRecording()
                                                    if (path != null) {
                                                        Toast.makeText(context, "تم حفظ التسجيل! جاري بدء المشروع...", Toast.LENGTH_SHORT).show()
                                                        onNewProject(null)
                                                    }
                                                }
                                            } else {
                                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isRecordingVoice) Color(0xFFEF4444).copy(alpha = 0.25f) else Color(0xFF1E293B))
                                    ) {
                                        Icon(
                                            imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = "تسجيل صوتي",
                                            tint = if (isRecordingVoice) Color(0xFFEF4444) else GoldPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Launch Button
                                    Button(
                                        onClick = {
                                            onNewProject(quickPromptText.ifBlank { null })
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "انطلق ⚡",
                                            color = DeepSlate,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Tri-Core Holographic Launchpads
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpatialLaunchpadCard(
                                title = "الوكيل الذكي",
                                subtitle = "AI Studio 🛸",
                                icon = Icons.Default.SmartToy,
                                accentColor = Color(0xFF00E5FF),
                                modifier = Modifier.weight(1f),
                                onClick = onAiStudioAgent
                            )
                            SpatialLaunchpadCard(
                                title = "الإنتاج السينمائي",
                                subtitle = "AI Director",
                                icon = Icons.Default.AutoAwesome,
                                accentColor = GoldPrimary,
                                modifier = Modifier.weight(1f),
                                onClick = onStudioGateway
                            )
                            SpatialLaunchpadCard(
                                title = "مِشكاة الهُدى",
                                subtitle = "المصحف والحديث",
                                icon = Icons.Default.Mosque,
                                accentColor = Color(0xFF10B981),
                                modifier = Modifier.weight(1f),
                                onClick = onQuranHub
                            )
                        }
                    }
                }

                // 2. Workspace Draft Recovery Banner
                if (autoSaveStatus.hasRecoverableDraft && onRestoreWorkspaceDraft != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GoldPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("مسودة محفوظة في ذاكرة العمل 💾", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(autoSaveStatus.draftSnippet.ifBlank { "استعد تعديلات الفيديو السابقة" }, color = Color(0xFF94A3B8), fontFamily = NotoSansFont, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onRestoreWorkspaceDraft() }) {
                                    Text("استعادة", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = { scope.launch { WorkspaceAutoSaveManager.clearAiDraft() } },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "تجاهل", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // 3. Sector Tabs Bar (HUD Selector)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpatialSectorTabButton(
                            title = "الاستوديوهات",
                            icon = Icons.Default.VideoLibrary,
                            isSelected = selectedSector == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedSector = 0 }
                        )
                        SpatialSectorTabButton(
                            title = "مِشكاة الهُدى",
                            icon = Icons.Default.Mosque,
                            isSelected = selectedSector == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedSector = 1 }
                        )
                        SpatialSectorTabButton(
                            title = "أدوات الذكاء",
                            icon = Icons.Default.Psychology,
                            isSelected = selectedSector == 2,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedSector = 2 }
                        )
                        SpatialSectorTabButton(
                            title = "المشاريع",
                            icon = Icons.Default.Folder,
                            isSelected = selectedSector == 3,
                            badgeCount = recentProjects.size,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedSector = 3 }
                        )
                    }
                }

                // 4. Sector Content Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    when (selectedSector) {
                        0 -> {
                            // Sector 0: Creative Studios Hub
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SpatialHudCard(
                                    title = "استوديو الوكيل الذكي // AI Studio 🛸",
                                    subtitle = "بيئة تطوير مستقلة قائمة على إعطاء الأوامر باللغة الطبيعية وبناء السحاب الفوري",
                                    badge = "AI Studio",
                                    badgeColor = Color(0xFF00E5FF),
                                    icon = Icons.Default.SmartToy,
                                    accentColor = Color(0xFF00E5FF),
                                    onClick = onAiStudioAgent
                                )
                                SpatialHudCard(
                                    title = "استوديو قبس الشامل 🎬",
                                    subtitle = "البوابة المركزية ومسارات الإنتاج الذكية",
                                    badge = "الرئيسي",
                                    badgeColor = GoldPrimary,
                                    icon = Icons.Default.AutoAwesome,
                                    accentColor = GoldPrimary,
                                    onClick = onStudioGateway
                                )
                                SpatialHudCard(
                                    title = "المحرر اليدوي الاحترافي ✂️",
                                    subtitle = "تايم لاين، مسارات، ومونتاج دقيق CapCut",
                                    badge = "Pro Editor",
                                    badgeColor = Color(0xFF38BDF8),
                                    icon = Icons.Default.MovieFilter,
                                    accentColor = Color(0xFF38BDF8),
                                    onClick = onProEditor
                                )
                                SpatialHudCard(
                                    title = "مصنع الريلز المؤتمت // MoneyPrinter Turbo ⚡",
                                    subtitle = "أتمتة إنتاج ريلز وشورتس فورية 9:16 بـ Edge-TTS العصبي المجاني",
                                    badge = "Turbo AI",
                                    badgeColor = Color(0xFF00F2FE),
                                    icon = Icons.Default.Bolt,
                                    accentColor = Color(0xFF00F2FE),
                                    onClick = onMoneyPrinterTurbo
                                )
                                SpatialHudCard(
                                    title = "استوديو استنساخ ودبلجة الأصوات // VoiceStudio 🎙️",
                                    subtitle = "استنساخ الأصوات Zero-Shot، الدبلجة الذكية، وبديل ElevenLabs المفتوح المصدر",
                                    badge = "Cloner AI",
                                    badgeColor = Color(0xFFA855F7),
                                    icon = Icons.Default.GraphicEq,
                                    accentColor = Color(0xFFA855F7),
                                    onClick = onVoiceStudio
                                )
                                SpatialHudCard(
                                    title = "استوديو الصور والمصغرات 🎨",
                                    subtitle = "تصميم بوسترات وأغلفة سينمائية بدقة 4K",
                                    badge = "Thumbnails",
                                    badgeColor = Color(0xFFF59E0B),
                                    icon = Icons.Default.Image,
                                    accentColor = Color(0xFFF59E0B),
                                    onClick = onPhotoStudio
                                )
                                SpatialHudCard(
                                    title = "ملقن الكاميرا الذكي 🎙️",
                                    subtitle = "تسجيل الفيديو مع قراءة النصوص والتحكم بالسرعة",
                                    badge = "Teleprompter",
                                    badgeColor = Color(0xFF10B981),
                                    icon = Icons.Default.Videocam,
                                    accentColor = Color(0xFF10B981),
                                    onClick = onTeleprompter
                                )
                                SpatialHudCard(
                                    title = "مستنسخ الأنماط البصرية 🪄",
                                    subtitle = "استخلاص الألوان، الحركة، والإيقاع من أي مقطع فيديو",
                                    badge = "Style Cloner",
                                    badgeColor = Color(0xFFA855F7),
                                    icon = Icons.Default.Style,
                                    accentColor = Color(0xFFA855F7),
                                    onClick = onVideoStyleCloner
                                )
                                SpatialHudCard(
                                    title = "استوديو يوتيوب والشورتس 📺",
                                    subtitle = "اقتطاع الصمت، توليد فصول الفيديو، وعناوين الـ SEO",
                                    badge = "YouTube Studio",
                                    badgeColor = Color(0xFFEF4444),
                                    icon = Icons.Default.SmartDisplay,
                                    accentColor = Color(0xFFEF4444),
                                    onClick = onYouTubeStudio
                                )
                                SpatialHudCard(
                                    title = "مكتبة الأنماط السينمائية 🎭",
                                    subtitle = "اختيار وتخصيص نمط المونتاج والهوية البصرية",
                                    badge = "Styles Library",
                                    badgeColor = Color(0xFF06B6D4),
                                    icon = Icons.Default.Palette,
                                    accentColor = Color(0xFF06B6D4),
                                    onClick = onStyleSelection
                                )
                            }
                        }
                        1 -> {
                            // Sector 1: Islamic Spiritual Suite
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SpatialHudCard(
                                    title = "القرآن الكريم والتجويد 📖",
                                    subtitle = "المصحف المرتل، أحكام التجويد، وتفسير الآيات",
                                    badge = "المصحف الشريف",
                                    badgeColor = Color(0xFF10B981),
                                    icon = Icons.Default.MenuBook,
                                    accentColor = Color(0xFF10B981),
                                    onClick = onQuranHub
                                )
                                SpatialHudCard(
                                    title = "استوديو الحديث الشريف 📜",
                                    subtitle = "صحيح البخاري ومسلم وبطاقات 4K مع التخريج المعتمد",
                                    badge = "الأحاديث الصحيحة",
                                    badgeColor = GoldPrimary,
                                    icon = Icons.Default.AutoStories,
                                    accentColor = GoldPrimary,
                                    onClick = onHadithStudio
                                )
                                SpatialHudCard(
                                    title = "الأذكار وحصن المسلم 📿",
                                    subtitle = "أذكار الصباح والمساء، عداد التسبيح الذكي، وسجل الحسنات",
                                    badge = "حصن المسلم",
                                    badgeColor = Color(0xFFEC4899),
                                    icon = Icons.Default.Favorite,
                                    accentColor = Color(0xFFEC4899),
                                    onClick = onAzkar
                                )
                                SpatialHudCard(
                                    title = "مواقيت الصلاة وبوصلة القبلة 🧭",
                                    subtitle = "حساب دقيق للمواقيت مع تنبيهات الأذان وبوصلة ثلاثية الأبعاد",
                                    badge = "المواقيت الحية",
                                    badgeColor = Color(0xFF0EA5E9),
                                    icon = Icons.Default.Explore,
                                    accentColor = Color(0xFF0EA5E9),
                                    onClick = onPrayerTimes
                                )
                                SpatialHudCard(
                                    title = "المكتبة الإسلامية الكبرى 📚",
                                    subtitle = "أمهات الكتب الإسلامية والتفاسير والعلوم الشرعية",
                                    badge = "كتب محققة",
                                    badgeColor = Color(0xFFF97316),
                                    icon = Icons.Default.LocalLibrary,
                                    accentColor = Color(0xFFF97316),
                                    onClick = onIslamicLibrary
                                )
                                SpatialHudCard(
                                    title = "سيرة الأكابر وتراجم الأئمة 🕌",
                                    subtitle = "تراجم موثقة لأئمة الهدى، الفقهاء، والمحدثين عبر التاريخ",
                                    badge = "تراجم الأئمة",
                                    badgeColor = Color(0xFF8B5CF6),
                                    icon = Icons.Default.School,
                                    accentColor = Color(0xFF8B5CF6),
                                    onClick = onScholarBiographies
                                )
                                SpatialHudCard(
                                    title = "منارة المعرفة وتزكية النفس 💡",
                                    subtitle = "مقالات إيمانية وبناء الوعي الدعوي والتزكية",
                                    badge = "تزكية ومعرفة",
                                    badgeColor = Color(0xFF14B8A6),
                                    icon = Icons.Default.Lightbulb,
                                    accentColor = Color(0xFF14B8A6),
                                    onClick = onKnowledgeHub
                                )
                            }
                        }
                        2 -> {
                            // Sector 2: AI Spatial Engines & Growth Tools
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SpatialHudCard(
                                    title = "مستشار الذكاء الاصطناعي 🤖",
                                    subtitle = "توليد سيناريوهات، أفكار إخراجية، وصياغة نصوص مؤثرة",
                                    badge = "AI Co-pilot",
                                    badgeColor = GoldPrimary,
                                    icon = Icons.Default.Psychology,
                                    accentColor = GoldPrimary,
                                    onClick = onAiAssistant
                                )
                                SpatialHudCard(
                                    title = "حارس المحتوى الشرعي 🛡️",
                                    subtitle = "تدقيق ومراجعة شرعية وأخلاقية لضمان سلامة المحتوى",
                                    badge = "Content Guard",
                                    badgeColor = Color(0xFF10B981),
                                    icon = Icons.Default.Shield,
                                    accentColor = Color(0xFF10B981),
                                    onClick = onContentGuard
                                )
                                SpatialHudCard(
                                    title = "مكتبة الصوتيات والتلاوات 🎵",
                                    subtitle = "أصوات طبيعية، خلفيات مؤثرة، وتلاوات خاشعة مرخصة",
                                    badge = "Audio Library",
                                    badgeColor = Color(0xFFEC4899),
                                    icon = Icons.Default.GraphicEq,
                                    accentColor = Color(0xFFEC4899),
                                    onClick = onAudioLibrary
                                )
                                SpatialHudCard(
                                    title = "الذوق الفني والأسلوب 🎨",
                                    subtitle = "تخصيص الهوية البصرية ونمطك المفضل في المونتاج",
                                    badge = "Taste Profile",
                                    badgeColor = Color(0xFF6366F1),
                                    icon = Icons.Default.Tune,
                                    accentColor = Color(0xFF6366F1),
                                    onClick = onNavigateToTasteProfile
                                )
                                SpatialHudCard(
                                    title = "لوحة الشرف وميدان الحسنات 🏆",
                                    subtitle = "رصيد النقاط والتنافس المبارك في نشر الخير والأثر",
                                    badge = "Leaderboard",
                                    badgeColor = Color(0xFFF59E0B),
                                    icon = Icons.Default.EmojiEvents,
                                    accentColor = Color(0xFFF59E0B),
                                    onClick = onLeaderboard
                                )
                                SpatialHudCard(
                                    title = "سوق الجوائز والمكافآت 🎁",
                                    subtitle = "استبدال نقاط الحسنات بمكافآت وتوسعات إنتاج حصرية",
                                    badge = "Rewards Hub",
                                    badgeColor = Color(0xFF14B8A6),
                                    icon = Icons.Default.CardGiftcard,
                                    accentColor = Color(0xFF14B8A6),
                                    onClick = onRewards
                                )
                                SpatialHudCard(
                                    title = "ترقية الحساب المميز VIP 💎",
                                    subtitle = "تصدير فائق الدقة 4K وخوادم سريعة وميزات حصرية",
                                    badge = "VIP Upgrade",
                                    badgeColor = GoldPrimary,
                                    icon = Icons.Default.WorkspacePremium,
                                    accentColor = GoldPrimary,
                                    onClick = onPremiumUpgrade
                                )
                                SpatialHudCard(
                                    title = "توثيق الواجهات البرمجية 📄",
                                    subtitle = "دليل الربط البرمجي للمطورين والمنصات الخارجية API",
                                    badge = "API Docs",
                                    badgeColor = Color(0xFF38BDF8),
                                    icon = Icons.Default.Code,
                                    accentColor = Color(0xFF38BDF8),
                                    onClick = onViewApiDocs
                                )
                            }
                        }
                        3 -> {
                            // Sector 3: Recent Projects Matrix
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (recentProjects.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "مشاريعك المحفوظة (${recentProjects.size})",
                                            color = GoldPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        TextButton(onClick = onViewProjects) {
                                            Text("عرض الكل", color = AiCyanLight, fontFamily = CairoFont, fontSize = 12.sp)
                                        }
                                    }

                                    recentProjects.take(6).forEach { project ->
                                        SpatialProjectItem(
                                            project = project,
                                            onClick = { onOpenProject(project) }
                                        )
                                    }
                                } else {
                                    SpatialEmptyProjects(onNewProject = { onNewProject(null) })
                                }
                            }
                        }
                    }
                }

                // 5. Developer Command Card (For Admins Only)
                if (isAdmin) {
                    Surface(
                        onClick = onDeveloperDashboard,
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color(0xFFEF4444), GoldPrimary))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "المراكز القيادية الستة ولوحة المطور 🛡️",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "مراقبة المفاتيح الحية (14) • موجه السرعة • اختبار صحة السيرفرات",
                                        color = Color(0xFF94A3B8),
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ==================== Spatial HUD Component Building Blocks ====================

@Composable
fun SpatialLaunchpadCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = {
            AppSoundManager.playClick()
            onClick()
        },
        shape = RoundedCornerShape(16.dp),
        color = CardSurface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontFamily = NotoSansFont,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SpatialSectorTabButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = {
            AppSoundManager.playClick()
            onClick()
        },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) GoldPrimary.copy(alpha = 0.18f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)) else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) GoldPrimary else Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (isSelected) Color.White else Color(0xFF64748B),
                    fontFamily = CairoFont,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.5.sp,
                    maxLines = 1
                )
                if (badgeCount != null && badgeCount > 0) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Surface(
                        color = GoldPrimary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "$badgeCount",
                            color = DeepSlate,
                            fontFamily = NotoSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpatialHudCard(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = {
            AppSoundManager.playClick()
            onClick()
        },
        shape = RoundedCornerShape(16.dp),
        color = CardSurface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(GoldPrimary.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f)))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badge,
                                color = badgeColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        color = Color(0xFF94A3B8),
                        fontFamily = NotoSansFont,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SpatialProjectItem(
    project: ProjectService.Project,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GoldPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = project.title.ifBlank { "مشروع سينمائي بدون عنوان" },
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "الحالة: ${if (project.status.isNotBlank()) project.status else "مسودة مكتملة"}",
                        color = Color(0xFF94A3B8),
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )
                }
            }
            Icon(Icons.Default.PlayCircle, contentDescription = "فتح المشروع", tint = GoldPrimary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun SpatialEmptyProjects(onNewProject: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "لا توجد مشاريع سابقة بعد",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ابدأ بإنتاج أول فيديو هادف بضغطة زر واحدة",
                color = Color(0xFF94A3B8),
                fontFamily = NotoSansFont,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onNewProject,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("بدء مشروع جديد", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ==================== Navigation Drawer Content ====================

@Composable
fun QabasNavigationDrawerContent(
    isAdmin: Boolean,
    onCloseDrawer: () -> Unit,
    onNavigateTo: (() -> Unit) -> Unit,
    onStudioGateway: () -> Unit,
    onReels: () -> Unit = {},
    onProEditor: () -> Unit,
    onMoneyPrinterTurbo: () -> Unit = {},
    onVoiceStudio: () -> Unit = {},
    onPhotoStudio: () -> Unit,
    onTeleprompter: () -> Unit,
    onVideoStyleCloner: () -> Unit,
    onYouTubeStudio: () -> Unit,
    onViewProjects: () -> Unit,
    onQuranHub: () -> Unit,
    onIslamicLibrary: () -> Unit,
    onAzkar: () -> Unit,
    onScholarBiographies: () -> Unit,
    onPrayerTimes: () -> Unit,
    onAudioLibrary: () -> Unit,
    onAiAssistant: () -> Unit,
    onContentGuard: () -> Unit,
    onLeaderboard: () -> Unit,
    onNavigateToTasteProfile: () -> Unit,
    onPremiumUpgrade: () -> Unit,
    onViewProfile: () -> Unit,
    onViewSettings: () -> Unit,
    onViewApiDocs: () -> Unit,
    onDeveloperDashboard: () -> Unit,
    onNavigateToRules: () -> Unit = {},
    onAiStudioAgent: () -> Unit = {},
    onViewDownloads: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate)
    ) {
        // Drawer Header with Branding & Identity
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            AsyncImage(
                model = R.drawable.polished_qabas_drawer_hud_1790877423928,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.6f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, DeepSlate)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CutCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(GoldPrimary.copy(alpha = 0.3f), GoldPrimary.copy(alpha = 0.05f))
                                    )
                                )
                                .border(1.2.dp, GoldPrimary.copy(alpha = 0.45f), CutCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "قَبَس",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "CORE_INTERFACE // v2.6.5",
                                color = GoldPrimary.copy(alpha = 0.6f),
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onCloseDrawer,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Role & Status Badge
        Surface(
            color = if (isAdmin) Color(0xFFEF4444).copy(alpha = 0.12f) else GoldPrimary.copy(alpha = 0.1f),
            border = BorderStroke(
                0.8.dp,
                if (isAdmin) Color(0xFFEF4444).copy(alpha = 0.4f) else GoldPrimary.copy(alpha = 0.35f)
            ),
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isAdmin) Color(0xFFEF4444) else GoldPrimary)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isAdmin) "SYSTEM_ADMIN // ROOT_ACCESS 🛡️" else "CREATIVE_MASTER // SYNC_ACTIVE ✨",
                    color = if (isAdmin) Color(0xFFFCA5A5) else GoldPrimary,
                    fontFamily = RobotoMonoFont,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

        // Scrollable Drawer Items
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Group 1: الاستوديو والإنتاج المرئي
            QabasDrawerSectionHeader(
                title = "الاستوديو والإنتاج المرئي",
                icon = Icons.Default.VideoLibrary
            )
            QabasDrawerItem(
                title = "استوديو قبس الشامل",
                subtitle = "البوابة المركزية ومسارات الإنتاج الذكية",
                icon = Icons.Default.AutoAwesome,
                iconTint = GoldPrimary,
                badgeText = "الرئيسي",
                badgeColor = GoldPrimary,
                onClick = { onNavigateTo(onStudioGateway) }
            )
            QabasDrawerItem(
                title = "استوديو الوكيل الذكي // AI Studio Agent",
                subtitle = "بيئة تطوير مستقلة قائمة على إعطاء الأوامر باللغة الطبيعية (مثل Google AI Studio)",
                icon = Icons.Default.SmartToy,
                iconTint = Color(0xFF00E5FF),
                badgeText = "AUTONOMOUS",
                badgeColor = Color(0xFF00E5FF),
                onClick = { onNavigateTo(onAiStudioAgent) }
            )
            QabasDrawerItem(
                title = "مركز التنزيلات والنماذج المحلية",
                subtitle = "إدارة نماذج GGUF بدون إنترنت، ريلز، صوتيات وذاكرة الجهاز",
                icon = Icons.Default.CloudDownload,
                iconTint = Color(0xFF00E5FF),
                badgeText = "OFFLINE LLM",
                badgeColor = Color(0xFF00E5FF),
                onClick = { onNavigateTo(onViewDownloads) }
            )
            QabasDrawerItem(
                title = "المحرر اليدوي الاحترافي",
                subtitle = "تايم لاين، مسارات، ومونتاج دقيق CapCut",
                icon = Icons.Default.MovieFilter,
                iconTint = Color(0xFF38BDF8),
                badgeText = "PRO",
                badgeColor = Color(0xFF38BDF8),
                onClick = { onNavigateTo(onProEditor) }
            )
            QabasDrawerItem(
                title = "ريلز قبس // Qabas Reels",
                subtitle = "مجتمع الإلهام ومقاطع الفيديو القصيرة",
                icon = Icons.Default.SlowMotionVideo,
                iconTint = GoldPrimary,
                badgeText = "NEW",
                badgeColor = GoldPrimary,
                onClick = { onNavigateTo(onReels) }
            )
            QabasDrawerItem(
                title = "مصنع الريلز // MoneyPrinter Turbo",
                subtitle = "أتمتة إنتاج شورتس وريلز 9:16 مع Edge-TTS العصبي المجاني",
                icon = Icons.Default.Bolt,
                iconTint = Color(0xFF00F2FE),
                badgeText = "TURBO",
                badgeColor = Color(0xFF00F2FE),
                onClick = { onNavigateTo(onMoneyPrinterTurbo) }
            )
            QabasDrawerItem(
                title = "استوديو الأصوات // VoiceStudio",
                subtitle = "استنساخ الأصوات ودبلجة الفيديوهات بدون قيود",
                icon = Icons.Default.GraphicEq,
                iconTint = Color(0xFFA855F7),
                badgeText = "CLONER",
                badgeColor = Color(0xFFA855F7),
                onClick = { onNavigateTo(onVoiceStudio) }
            )
            QabasDrawerItem(
                title = "استوديو الصور والمصغرات",
                subtitle = "تصميم أغلفة ومصغرات جذابة للفيديوهات",
                icon = Icons.Default.AddPhotoAlternate,
                iconTint = Color(0xFFF472B6),
                onClick = { onNavigateTo(onPhotoStudio) }
            )
            QabasDrawerItem(
                title = "ملقن الكاميرا الذكي",
                subtitle = "تسجيل الفيديو مع قراءة النصوص والتحكم بالسرعة",
                icon = Icons.Default.Videocam,
                iconTint = Color(0xFF34D399),
                onClick = { onNavigateTo(onTeleprompter) }
            )
            QabasDrawerItem(
                title = "مستنسخ الأنماط البصرية",
                subtitle = "تحليل وتكرار أساليب المونتاج الناجحة",
                icon = Icons.Default.Palette,
                iconTint = Color(0xFFA78BFA),
                onClick = { onNavigateTo(onVideoStyleCloner) }
            )
            QabasDrawerItem(
                title = "استوديو يوتيوب والشورتس",
                subtitle = "أدوات نشر وتحسين لقنوات الفيديو والريلز",
                icon = Icons.Default.Subscriptions,
                iconTint = Color(0xFFF87171),
                onClick = { onNavigateTo(onYouTubeStudio) }
            )
            QabasDrawerItem(
                title = "مشاريعي ومسوداتي",
                subtitle = "جميع الأعمال السابقة والمسودات المحفوظة",
                icon = Icons.Default.FolderOpen,
                iconTint = Color(0xFFFBBF24),
                onClick = { onNavigateTo(onViewProjects) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Group 2: واحة مِشكاة الهُدى والعلوم الشرعية
            QabasDrawerSectionHeader(
                title = "مِشكاة الهُدى والعلوم الشرعية",
                icon = Icons.Default.Mosque
            )
            QabasDrawerItem(
                title = "مِشكاة الهُدى العامة",
                subtitle = "واحة النور والقرآن والزاد الإيماني",
                icon = Icons.Default.Mosque,
                iconTint = GoldPrimary,
                onClick = { onNavigateTo(onQuranHub) }
            )
            QabasDrawerItem(
                title = "القرآن الكريم والتجويد",
                subtitle = "المصحف الشريف، أحكام التجويد، والتلاوات الخاشعة",
                icon = Icons.Default.MenuBook,
                iconTint = Color(0xFF10B981),
                onClick = { onNavigateTo(onQuranHub) }
            )
            QabasDrawerItem(
                title = "المكتبة الإسلامية",
                subtitle = "أمهات كتب التفسير، الحديث، وعقيدة أهل السنة",
                icon = Icons.Default.LibraryBooks,
                iconTint = Color(0xFFF59E0B),
                onClick = { onNavigateTo(onIslamicLibrary) }
            )
            QabasDrawerItem(
                title = "الأذكار وحصن المسلم",
                subtitle = "أذكار الصباح والمساء، الرقية، والسبحة الذكية",
                icon = Icons.Default.Favorite,
                iconTint = Color(0xFFEC4899),
                onClick = { onNavigateTo(onAzkar) }
            )
            QabasDrawerItem(
                title = "سيرة الأكابر وتراجم الأئمة",
                subtitle = "مواقف صمود علماء وسلف الأمة المباركة",
                icon = Icons.Default.PersonSearch,
                iconTint = Color(0xFF06B6D4),
                onClick = { onNavigateTo(onScholarBiographies) }
            )
            QabasDrawerItem(
                title = "الأذان ومواقيت الصلاة",
                subtitle = "مواقيت دقيقة، أصوات الأذان، وغرفة التحكم",
                icon = Icons.Default.NotificationsActive,
                iconTint = Color(0xFF3B82F6),
                onClick = { onNavigateTo(onPrayerTimes) }
            )
            QabasDrawerItem(
                title = "مكتبة الصوتيات الدعوية",
                subtitle = "خطب، محاضرات، ومؤثرات صوتية مباحة",
                icon = Icons.Default.Audiotrack,
                iconTint = Color(0xFF8B5CF6),
                onClick = { onNavigateTo(onAudioLibrary) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Group 3: الأدوات الذكية والمجتمع
            QabasDrawerSectionHeader(
                title = "الأدوات الذكية والانتشار",
                icon = Icons.Default.AutoAwesome
            )
            QabasDrawerItem(
                title = "مستشار الذكاء الاصطناعي",
                subtitle = "توليد نصوص دعوية وأفكار إخراجية مبتكرة",
                icon = Icons.Default.SmartToy,
                iconTint = AiCyan,
                badgeText = "AI",
                badgeColor = AiCyan,
                onClick = { onNavigateTo(onAiAssistant) }
            )
            QabasDrawerItem(
                title = "حارس المحتوى الشرعي",
                subtitle = "تدقيق ومراجعة المحتوى لضمان السلامة الشرعية",
                icon = Icons.Default.VerifiedUser,
                iconTint = Color(0xFF10B981),
                onClick = { onNavigateTo(onContentGuard) }
            )
            QabasDrawerItem(
                title = "لوحة الشرف وميدان الحسنات",
                subtitle = "رصيد النقاط والتنافس المبارك في نشر الخير",
                icon = Icons.Default.EmojiEvents,
                iconTint = GoldPrimary,
                onClick = { onNavigateTo(onLeaderboard) }
            )
            QabasDrawerItem(
                title = "الذوق الفني والأسلوب",
                subtitle = "تخصيص الهوية البصرية ونمطك المفضل في المونتاج",
                icon = Icons.Default.Tune,
                iconTint = Color(0xFF6366F1),
                onClick = { onNavigateTo(onNavigateToTasteProfile) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Group 4: الحساب والإدارة
            QabasDrawerSectionHeader(
                title = "الحساب والإدارة",
                icon = Icons.Default.Settings
            )
            QabasDrawerItem(
                title = "ترقية الحساب المميز (VIP)",
                subtitle = "تصدير فائق الدقة 4K، خوادم سريعة، وميزات حصرية",
                icon = Icons.Default.WorkspacePremium,
                iconTint = GoldPrimary,
                badgeText = "VIP",
                badgeColor = GoldPrimary,
                onClick = { onNavigateTo(onPremiumUpgrade) }
            )
            QabasDrawerItem(
                title = "الملف الشخصي",
                subtitle = "إدارة الحساب، الإحصائيات، وسجل الإنجازات",
                icon = Icons.Default.AccountCircle,
                iconTint = Color(0xFF94A3B8),
                onClick = { onNavigateTo(onViewProfile) }
            )
            QabasDrawerItem(
                title = "الإعدادات العامة",
                subtitle = "المظهر، التنبيهات، مفاتيح الـ API، واللغة",
                icon = Icons.Default.Settings,
                iconTint = Color(0xFFCBD5E1),
                onClick = { onNavigateTo(onViewSettings) }
            )
            QabasDrawerItem(
                title = "توثيق الواجهات البرمجية (API)",
                subtitle = "دليل الربط البرمجي للمطورين والمنصات الخارجية",
                icon = Icons.Default.Code,
                iconTint = Color(0xFF38BDF8),
                onClick = { onNavigateTo(onViewApiDocs) }
            )
            QabasDrawerItem(
                title = "القواعد والسياسات الموحدة",
                subtitle = "شروط الاستخدام، سياسة الخصوصية، وقواعد المجتمع",
                icon = Icons.Default.Gavel,
                iconTint = Color(0xFFF59E0B),
                onClick = { onNavigateTo(onNavigateToRules) }
            )

            if (isAdmin) {
                QabasDrawerItem(
                    title = "لوحة تحكم المطورين",
                    subtitle = "تشخيص الخوادم، مفاتيح الذكاء، ومراقبة النظام",
                    icon = Icons.Default.AdminPanelSettings,
                    iconTint = Color(0xFFEF4444),
                    badgeText = "ROOT",
                    badgeColor = Color(0xFFEF4444),
                    onClick = { onNavigateTo(onDeveloperDashboard) }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Footer Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "﴿ ادْعُ إِلَىٰ سَبِيلِ رَبِّكَ بِالْحِكْمَةِ وَالْمَوْعِظَةِ الْحَسَنَةِ ﴾",
                        color = GoldPrimary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "قَبَس v2.5 • إصدار الإنتاج المبارك",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontFamily = NotoSansFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QabasDrawerItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    badgeText: String? = null,
    badgeColor: Color = GoldPrimary,
    onClick: () -> Unit
) {
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        backgroundColor = Color(0xFF1E293B).copy(alpha = 0.1f),
        borderColor = Color(0xFF334155).copy(alpha = 0.2f),
        shape = CutCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CutCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.08f))
                    .border(0.6.dp, iconTint.copy(alpha = 0.3f), CutCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badgeText != null) {
                        Surface(
                            color = badgeColor.copy(alpha = 0.1f),
                            border = BorderStroke(0.6.dp, badgeColor.copy(alpha = 0.4f)),
                            shape = CutCornerShape(4.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = RobotoMonoFont,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF64748B),
                    fontFamily = RobotoMonoFont,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun QabasDrawerSectionHeader(title: String, icon: ImageVector? = null) {
    val primaryColor = qabasPrimary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp)
                .background(primaryColor.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            color = primaryColor.copy(alpha = 0.7f),
            fontFamily = CairoFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.width(10.dp))
        HorizontalDivider(
            color = Color(0xFF334155).copy(alpha = 0.3f),
            modifier = Modifier.weight(1f)
        )
    }
}

