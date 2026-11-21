package com.qabas.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.TestTags
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentProjects: List<ProjectService.Project>,
    onNewProject: (String?) -> Unit,
    onNewProjectWithAudio: (String?, String?) -> Unit = { text, _ -> onNewProject(text) },
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
    onMishkatHub: () -> Unit = {},
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
            onNewProjectWithAudio = onNewProjectWithAudio,
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
            onMishkatHub = onMishkatHub,
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

    val theme = QabasTheme.current
    val context = LocalContext.current
    val authState by SupabaseRepository.getInstance().authState.collectAsState()
    val verifiedRole by SupabaseServices.Auth.verifiedRole.collectAsState()
    val isAdmin = remember(authState, verifiedRole) {
        SupabaseServices.Auth.isAdminRole(verifiedRole) ||
            CloudServices.isOwnerAccount(SupabaseServices.Auth.getCurrentUserEmail())
    }
    val isDeveloper = remember(authState, verifiedRole) {
        AdminGuard.isDashboardAccessAllowed(context)
    }
    val networkStatus by UserPreferencesManager.networkStatus.collectAsState()
    val autoSaveStatus by WorkspaceAutoSaveManager.status.collectAsState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentAudioTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isAudioPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    var quickPromptText by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    val recorderHelper = remember { AudioRecorderHelper(context) }

    // يبدأ المنتج من مساحة المسلم، مع إبقاء الاستوديو والمشاريع والذكاء الاصطناعي متاحة من التبويبات.
    var selectedSector by remember { mutableIntStateOf(1) }
    val activeAiEngine by AiModelLatencyRouter.activeEngine.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val ok = recorderHelper.startRecording()
            if (ok) {
                isRecordingVoice = true
                Toast.makeText(context, Translator.tr("بدأ التسجيل... تحدث بفكرتك الدعوية"), Toast.LENGTH_SHORT).show()
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
        QabasExitConfirmationDialog(
            onDismiss = { showExitDialog = false }
        )
    }

    val promotionCelebration by RolePromotionNotifier.celebrationEvent.collectAsState()
    if (promotionCelebration != null) {
        val celebration = promotionCelebration!!
        AlertDialog(
            onDismissRequest = { RolePromotionNotifier.dismissCelebration() },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Surface(
                    shape = CircleShape,
                    color = theme.primary.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "مبارك! تم تفعيل رتبة المطور",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = theme.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = celebration.message,
                        fontFamily = NotoSansFont,
                        fontSize = 13.5.sp,
                        color = theme.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = theme.secondarySurface
                    ) {
                        Text(
                            text = "الرتبة السحابية: ${celebration.roleTitle}",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = theme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        RolePromotionNotifier.dismissCelebration()
                        onDeveloperDashboard()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("فتح لوحة المطور", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { RolePromotionNotifier.dismissCelebration() }) {
                    Text("إغلاق", color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = theme.background,
                drawerContentColor = theme.textPrimary,
                modifier = Modifier.widthIn(max = 330.dp)
            ) {
                QabasNavigationDrawerContent(
                    isAdmin = isAdmin,
                    isDrawerOpen = drawerState.isOpen,
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onNavigateTo = { action ->
                        scope.launch {
                            drawerState.close()
                            action()
                        }
                    },
                    onStudioGateway = onStudioGateway,
                    onReels = onReels,
                    onProEditor = onProEditor,
                    onMoneyPrinterTurbo = onMoneyPrinterTurbo,
                    onVoiceStudio = onVoiceStudio,
                    onPhotoStudio = onPhotoStudio,
                    onTeleprompter = onTeleprompter,
                    onVideoStyleCloner = onVideoStyleCloner,
                    onYouTubeStudio = onYouTubeStudio,
                    onViewProjects = onViewProjects,
                    onMishkatHub = onMishkatHub,
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
            modifier = Modifier.testTag(TestTags.HOME_SCREEN),
            containerColor = theme.background,
            bottomBar = {
                Column {
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
                                color = theme.cardSurface,
                                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f)),
                                shadowElevation = if (theme.isDark) 0.dp else 4.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(theme.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            track.title,
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            track.artist,
                                            color = theme.textSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val homeAudioPlayTint by animateColorAsState(
                                        targetValue = if (isAudioPlaying) theme.success else theme.primary,
                                        animationSpec = tween(durationMillis = 300),
                                        label = "home_audio_play_tint"
                                    )
                                    IconButton(
                                        onClick = { AudioPlaybackManager.togglePlayState(context) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            if (isAudioPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                            contentDescription = if (isAudioPlaying) "إيقاف مؤقت" else "تشغيل",
                                            tint = homeAudioPlayTint,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    bottomBar()
                }
            },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.background)
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = Translator.tr("القائمة الجانبية"),
                                    tint = theme.primary
                                )
                            }
                            Text(
                                text = "مساعد المسلم",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (netIcon, netColor) = when (networkStatus) {
                                ConnectivityObserver.Status.Available -> Icons.Default.Wifi to theme.success
                                else -> Icons.Default.WifiOff to theme.destructive
                            }
                            IconButton(
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        if (networkStatus == ConnectivityObserver.Status.Available) "متصل بالإنترنت" else "أنت الآن في وضع عدم الاتصال",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(netIcon, contentDescription = "حالة الاتصال", tint = netColor, modifier = Modifier.size(20.dp))
                            }

                            IconButton(
                                onClick = { if (isAdmin) onDeveloperDashboard() else onViewApiDocs() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAdmin) Icons.Default.Terminal else Icons.Default.AutoAwesome,
                                    contentDescription = if (isAdmin) "لوحة المطور" else "محرك الذكاء: $activeAiEngine",
                                    tint = if (activeAiEngine == "Groq") theme.success else theme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = onViewDownloads,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("home_topbar_downloads_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "مركز التنزيلات", tint = theme.accent, modifier = Modifier.size(20.dp))
                            }

                            IconButton(
                                onClick = onViewNotifications,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.NotificationsNone, contentDescription = "الإشعارات", tint = theme.textPrimary, modifier = Modifier.size(21.dp))
                            }

                            IconButton(
                                onClick = onViewProfile,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "الملف الشخصي", tint = theme.primary, modifier = Modifier.size(23.dp))
                            }

                            IconButton(
                                onClick = onViewSettings,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("home_topbar_settings_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = theme.textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.45f))
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(2.dp))

                QabasSmartTipsBar(
                    isDeveloper = isDeveloper,
                    onNavigateToDeveloperDashboard = onDeveloperDashboard
                )

                // Hero Studio Card
                AppleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = 18.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "« ادْعُ إِلَىٰ سَبِيلِ رَبِّكَ بِالْحِكْمَةِ وَالْمَوْعِظَةِ الْحَسَنَةِ »",
                                    color = theme.primary,
                                    fontFamily = AmiriFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                color = theme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "نواة الإنتاج",
                                    color = theme.primary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Quick Prompt & Voice Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.secondarySurface.copy(alpha = 0.55f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quickPromptText,
                                onValueChange = { quickPromptText = it },
                                placeholder = {
                                    Text(
                                        text = if (isRecordingVoice) "جاري الاستماع لصوتك..." else "اكتب فكرة مقطعك، أو آية، أو سيناريو...",
                                        color = theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.5.sp
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = theme.textPrimary,
                                    unfocusedTextColor = theme.textPrimary,
                                    cursorColor = theme.primary
                                ),
                                singleLine = true
                            )

                            IconButton(
                                onClick = {
                                    if (isRecordingVoice) {
                                        isRecordingVoice = false
                                        scope.launch {
                                            val path = recorderHelper.stopRecording()
                                            if (path != null) {
                                                Toast.makeText(context, "تم حفظ التسجيل! جاري بدء المشروع...", Toast.LENGTH_SHORT).show()
                                                quickPromptText = ""
                                                onNewProjectWithAudio(null, path.absolutePath)
                                            }
                                        }
                                    } else {
                                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isRecordingVoice) theme.destructive.copy(alpha = 0.16f) else theme.cardSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "تسجيل صوتي",
                                        tint = if (isRecordingVoice) theme.destructive else theme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (isRecordingVoice) {
                                        Toast.makeText(context, "أوقف التسجيل أولاً قبل بدء المشروع", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val prompt = quickPromptText.ifBlank { null }
                                        quickPromptText = ""
                                        onNewProject(prompt)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.primary,
                                    contentColor = theme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text(
                                    text = "انطلق",
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Tri-Core Launchpads
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpatialLaunchpadCard(
                                title = "مِشكاة الهُدى",
                                subtitle = "المصحف والحديث",
                                icon = Icons.Default.Mosque,
                                accentColor = theme.accent,
                                modifier = Modifier.weight(1f),
                                onClick = onMishkatHub
                            )
                            SpatialLaunchpadCard(
                                title = "الأذكار",
                                subtitle = "حصن المسلم",
                                icon = Icons.Default.Favorite,
                                accentColor = theme.success,
                                modifier = Modifier.weight(1f),
                                onClick = onAzkar
                            )
                            SpatialLaunchpadCard(
                                title = "مواقيت الصلاة",
                                subtitle = "الصلاة والقبلة",
                                icon = Icons.Default.Explore,
                                accentColor = theme.primary,
                                modifier = Modifier.weight(1f),
                                onClick = onPrayerTimes
                            )
                        }
                    }
                }

                // Workspace Draft Recovery Banner
                if (autoSaveStatus.hasRecoverableDraft && onRestoreWorkspaceDraft != null) {
                    AppleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
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
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, tint = theme.primary, modifier = Modifier.size(19.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("مسودة محفوظة في ذاكرة العمل", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(autoSaveStatus.draftSnippet.ifBlank { "استعد تعديلات الفيديو السابقة" }, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onRestoreWorkspaceDraft() }) {
                                    Text("استعادة", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                IconButton(
                                    onClick = { scope.launch { WorkspaceAutoSaveManager.clearAiDraft() } },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "تجاهل", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Sector Tabs Bar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.9f else 0.72f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SpatialSectorTabButton(
                            title = "مساحة المسلم",
                            icon = Icons.Default.Mosque,
                            isSelected = selectedSector == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedSector = 1 }
                        )
                        SpatialSectorTabButton(
                            title = "الاستوديوهات",
                            icon = Icons.Default.VideoLibrary,
                            isSelected = selectedSector == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedSector = 0 }
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

                // Sector Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    when (selectedSector) {
                        0 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SpatialHudCard(
                                    title = "استوديو الوكيل الذكي",
                                    subtitle = "بيئة تطوير قائمة على إعطاء الأوامر باللغة الطبيعية",
                                    badge = "AI Studio",
                                    badgeColor = Color(0xFF0A84FF),
                                    icon = Icons.Default.SmartToy,
                                    accentColor = Color(0xFF0A84FF),
                                    onClick = onAiStudioAgent
                                )
                                SpatialHudCard(
                                    title = "مركز الإنتاج المتكامل",
                                    subtitle = "البوابة المركزية ومسارات الإنتاج الذكية",
                                    badge = "الرئيسي",
                                    badgeColor = theme.primary,
                                    icon = Icons.Default.AutoAwesome,
                                    accentColor = theme.primary,
                                    onClick = onStudioGateway
                                )
                                SpatialHudCard(
                                    title = "المحرر اليدوي الاحترافي",
                                    subtitle = "تايم لاين، مسارات، ومونتاج دقيق",
                                    badge = "Pro",
                                    badgeColor = Color(0xFF32ADE6),
                                    icon = Icons.Default.MovieFilter,
                                    accentColor = Color(0xFF32ADE6),
                                    onClick = onProEditor
                                )
                                SpatialHudCard(
                                    title = "مصنع الريلز المؤتمت",
                                    subtitle = "أتمتة إنتاج ريلز وشورتس 9:16 بالتعليق العصبي",
                                    badge = "Turbo",
                                    badgeColor = Color(0xFF06B6D4),
                                    icon = Icons.Default.Bolt,
                                    accentColor = Color(0xFF06B6D4),
                                    onClick = onMoneyPrinterTurbo
                                )
                                SpatialHudCard(
                                    title = "استوديو استنساخ ودبلجة الأصوات",
                                    subtitle = "استنساخ الأصوات والدبلجة الذكية للمقاطع",
                                    badge = "Voice",
                                    badgeColor = Color(0xFFAF52DE),
                                    icon = Icons.Default.GraphicEq,
                                    accentColor = Color(0xFFAF52DE),
                                    onClick = onVoiceStudio
                                )
                                SpatialHudCard(
                                    title = "استوديو الصور والمصغرات",
                                    subtitle = "تصميم بوسترات وأغلفة بدقة 4K",
                                    badge = "صور",
                                    badgeColor = Color(0xFFFF9500),
                                    icon = Icons.Default.Image,
                                    accentColor = Color(0xFFFF9500),
                                    onClick = onPhotoStudio
                                )
                                SpatialHudCard(
                                    title = "ملقن الكاميرا الذكي",
                                    subtitle = "تسجيل الفيديو مع قراءة النصوص والتحكم بالسرعة",
                                    badge = "ملقن",
                                    badgeColor = theme.success,
                                    icon = Icons.Default.Videocam,
                                    accentColor = theme.success,
                                    onClick = onTeleprompter
                                )
                                SpatialHudCard(
                                    title = "مستنسخ الأنماط البصرية",
                                    subtitle = "استخلاص الألوان والحركة والإيقاع من أي مقطع",
                                    badge = "أنماط",
                                    badgeColor = Color(0xFF5856D6),
                                    icon = Icons.Default.Style,
                                    accentColor = Color(0xFF5856D6),
                                    onClick = onVideoStyleCloner
                                )
                                SpatialHudCard(
                                    title = "استوديو يوتيوب والشورتس",
                                    subtitle = "اقتطاع الصمت وتوليد الفصول وعناوين البحث",
                                    badge = "YouTube",
                                    badgeColor = Color(0xFFFF3B30),
                                    icon = Icons.Default.SmartDisplay,
                                    accentColor = Color(0xFFFF3B30),
                                    onClick = onYouTubeStudio
                                )
                                SpatialHudCard(
                                    title = "مكتبة الأنماط السينمائية",
                                    subtitle = "اختيار وتخصيص نمط المونتاج والهوية البصرية",
                                    badge = "المكتبة",
                                    badgeColor = Color(0xFF06B6D4),
                                    icon = Icons.Default.Palette,
                                    accentColor = Color(0xFF06B6D4),
                                    onClick = onStyleSelection
                                )
                            }
                        }
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SpatialHudCard(
                                    title = "استوديو الحديث الشريف",
                                    subtitle = "صحيح البخاري ومسلم وبطاقات 4K مع التخريج المعتمد",
                                    badge = "الأحاديث",
                                    badgeColor = theme.primary,
                                    icon = Icons.Default.AutoStories,
                                    accentColor = theme.primary,
                                    onClick = onHadithStudio
                                )
                                SpatialHudCard(
                                    title = "الأذكار وحصن المسلم",
                                    subtitle = "أذكار الصباح والمساء وعداد التسبيح وسجل الحسنات",
                                    badge = "حصن المسلم",
                                    badgeColor = Color(0xFFFF2D55),
                                    icon = Icons.Default.Favorite,
                                    accentColor = Color(0xFFFF2D55),
                                    onClick = onAzkar
                                )
                                SpatialHudCard(
                                    title = "مواقيت الصلاة وبوصلة القبلة",
                                    subtitle = "حساب دقيق للمواقيت مع تنبيهات الأذان والقبلة",
                                    badge = "المواقيت",
                                    badgeColor = Color(0xFF0A84FF),
                                    icon = Icons.Default.Explore,
                                    accentColor = Color(0xFF0A84FF),
                                    onClick = onPrayerTimes
                                )
                                SpatialHudCard(
                                    title = "المكتبة الإسلامية الكبرى",
                                    subtitle = "أمهات الكتب الإسلامية والتفاسير والعلوم الشرعية",
                                    badge = "كتب محققة",
                                    badgeColor = Color(0xFFFF9500),
                                    icon = Icons.Default.LocalLibrary,
                                    accentColor = Color(0xFFFF9500),
                                    onClick = onIslamicLibrary
                                )
                                SpatialHudCard(
                                    title = "سيرة الأكابر وتراجم الأئمة",
                                    subtitle = "تراجم موثقة لأئمة الهدى والفقهاء والمحدثين",
                                    badge = "التراجم",
                                    badgeColor = Color(0xFF5856D6),
                                    icon = Icons.Default.School,
                                    accentColor = Color(0xFF5856D6),
                                    onClick = onScholarBiographies
                                )
                                SpatialHudCard(
                                    title = "منارة المعرفة وتزكية النفس",
                                    subtitle = "مقالات إيمانية وبناء الوعي الدعوي والتزكية",
                                    badge = "معرفة",
                                    badgeColor = Color(0xFF14B8A6),
                                    icon = Icons.Default.Lightbulb,
                                    accentColor = Color(0xFF14B8A6),
                                    onClick = onKnowledgeHub
                                )
                            }
                        }
                        2 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SpatialHudCard(
                                    title = "مستشار الذكاء الاصطناعي",
                                    subtitle = "توليد سيناريوهات وأفكار إخراجية وصياغة نصوص مؤثرة",
                                    badge = "AI",
                                    badgeColor = theme.primary,
                                    icon = Icons.Default.Psychology,
                                    accentColor = theme.primary,
                                    onClick = onAiAssistant
                                )
                                SpatialHudCard(
                                    title = "حارس المحتوى الشرعي",
                                    subtitle = "تدقيق ومراجعة شرعية وأخلاقية لضمان سلامة المحتوى",
                                    badge = "تدقيق",
                                    badgeColor = theme.success,
                                    icon = Icons.Default.Shield,
                                    accentColor = theme.success,
                                    onClick = onContentGuard
                                )
                                SpatialHudCard(
                                    title = "مكتبة الصوتيات والتلاوات",
                                    subtitle = "أصوات طبيعية وخلفيات مؤثرة وتلاوات خاشعة",
                                    badge = "صوتيات",
                                    badgeColor = Color(0xFFFF2D55),
                                    icon = Icons.Default.GraphicEq,
                                    accentColor = Color(0xFFFF2D55),
                                    onClick = onAudioLibrary
                                )
                                SpatialHudCard(
                                    title = "الذوق الفني والأسلوب",
                                    subtitle = "تخصيص الهوية البصرية ونمطك المفضل في المونتاج",
                                    badge = "الذوق",
                                    badgeColor = Color(0xFF5856D6),
                                    icon = Icons.Default.Tune,
                                    accentColor = Color(0xFF5856D6),
                                    onClick = onNavigateToTasteProfile
                                )
                                SpatialHudCard(
                                    title = "لوحة الشرف وميدان الحسنات",
                                    subtitle = "رصيد النقاط والتنافس المبارك في نشر الخير والأثر",
                                    badge = "الترتيب",
                                    badgeColor = Color(0xFFFF9500),
                                    icon = Icons.Default.EmojiEvents,
                                    accentColor = Color(0xFFFF9500),
                                    onClick = onLeaderboard
                                )
                                SpatialHudCard(
                                    title = "سوق الجوائز والمكافآت",
                                    subtitle = "استبدال نقاط الحسنات بمكافآت وتوسعات إنتاج",
                                    badge = "المكافآت",
                                    badgeColor = Color(0xFF14B8A6),
                                    icon = Icons.Default.CardGiftcard,
                                    accentColor = Color(0xFF14B8A6),
                                    onClick = onRewards
                                )
                                SpatialHudCard(
                                    title = "ترقية الحساب المميز VIP",
                                    subtitle = "تصدير فائق الدقة 4K وخوادم سريعة وميزات حصرية",
                                    badge = "VIP",
                                    badgeColor = theme.primary,
                                    icon = Icons.Default.WorkspacePremium,
                                    accentColor = theme.primary,
                                    onClick = onPremiumUpgrade
                                )
                                SpatialHudCard(
                                    title = "توثيق الواجهات البرمجية",
                                    subtitle = "دليل الربط البرمجي للمطورين والمنصات الخارجية",
                                    badge = "API",
                                    badgeColor = Color(0xFF32ADE6),
                                    icon = Icons.Default.Code,
                                    accentColor = Color(0xFF32ADE6),
                                    onClick = onViewApiDocs
                                )
                            }
                        }
                        3 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (recentProjects.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "مشاريعك المحفوظة (${recentProjects.size})",
                                            color = theme.textPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        TextButton(onClick = onViewProjects) {
                                            Text("عرض الكل", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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

                // Developer Command Card (For Admins Only)
                if (isAdmin) {
                    AppleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onClick = onDeveloperDashboard
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.destructive),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "المراكز القيادية ولوحة المطور",
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "مراقبة المفاتيح الحية • موجه السرعة • تشخيص السحابة",
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                AppRealVersionFooterBadge(
                    onViewDownloads = onViewDownloads
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// ==================== Shared Home Component Building Blocks ====================

@Composable
fun SpatialLaunchpadCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Surface(
        onClick = {
            AppSoundManager.playClick()
            onClick()
        },
        shape = RoundedCornerShape(14.dp),
        color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.72f else 0.55f),
        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.4f)),
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    val theme = QabasTheme.current
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) theme.cardSurface else Color.Transparent,
        animationSpec = tween(220),
        label = "sector_bg"
    )
    Surface(
        onClick = {
            AppSoundManager.playClick()
            onClick()
        },
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        shadowElevation = if (isSelected && !theme.isDark) 1.5.dp else 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) theme.primary else theme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (isSelected) theme.textPrimary else theme.textSecondary,
                    fontFamily = CairoFont,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeCount != null && badgeCount > 0) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Surface(
                        color = theme.primary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "$badgeCount",
                            color = theme.onPrimary,
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
    val theme = QabasTheme.current
    AppleCard(
        onClick = {
            AppSoundManager.playClick()
            onClick()
        }
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = badgeColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = badge,
                                color = badgeColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = theme.textSecondary.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SpatialProjectItem(
    project: ProjectService.Project,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = theme.primary, modifier = Modifier.size(21.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = project.title.ifBlank { "مشروع فيديو بدون عنوان" },
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "الحالة: ${if (project.status.isNotBlank()) project.status else "مسودة مكتملة"}",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp
                    )
                }
            }
            Icon(Icons.Default.PlayCircle, contentDescription = "فتح المشروع", tint = theme.primary, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun SpatialEmptyProjects(onNewProject: () -> Unit) {
    val theme = QabasTheme.current
    AppleCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(theme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = theme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "لا توجد مشاريع سابقة بعد",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ابدأ بإنتاج أول فيديو هادف بضغطة زر واحدة",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            ApplePrimaryButton(
                text = "بدء مشروع جديد",
                icon = Icons.Default.Add,
                onClick = onNewProject
            )
        }
    }
}
