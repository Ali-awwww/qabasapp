package com.qabas.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.TestTags
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialCommandHomeScreen(
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
    // وجهات نتائج البحث العام — الافتراضي يفتح قسم كل نتيجة (تُمرَّر من
    // AppNavigation عند توفر تنقّل عميق إلى الآية/الحديث/الكتاب/المقطع).
    // واجهة البحث العام وشريط «أكمل» من الدفعة 3؛ تُمرَّران إلى
    // HomeGlobalSearch وHomeContinueRow.
    onOpenQuranVerse: (Int, Int) -> Unit = { _, _ -> onMishkatHub() },
    onOpenHadith: (String) -> Unit = { onHadithStudio() },
    onOpenLibraryBook: (String) -> Unit = { onIslamicLibrary() },
    onOpenReel: (String) -> Unit = { onReels() },
    onOpenCreator: (String, String) -> Unit = { _, _ -> onReels() },
    // فتح قارئ المصحف عند موضع محدد (سورة، آية، صفحة) — الدفعة 5 الجزء C.
    onOpenMushaf: (Int, Int, Int?) -> Unit = { _, _, _ -> onMishkatHub() },
    // ق103: فتح قارئ المصحف من الدرج بلا موضع — يستأنف آخر قراءة.
    onMushafReader: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val reducedMotion by UserPreferencesManager.reducedMotion.collectAsState()
    val scope = rememberCoroutineScope()
    val authState by SupabaseRepository.getInstance().authState.collectAsState()
    val verifiedRole by SupabaseServices.Auth.verifiedRole.collectAsState()
    val isAdmin = remember(authState, verifiedRole) {
        SupabaseServices.Auth.isAdminRole(verifiedRole) ||
            CloudServices.isOwnerAccount(SupabaseServices.Auth.getCurrentUserEmail())
    }
    // ق103: الضيف يصل الرئيسية فشارة الدرج يجب ألا تدعي له حساباً نشطاً.
    val isGuest = remember(authState) {
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
            .getBoolean("is_guest", false)
    }
    val isDeveloper = remember(authState, verifiedRole) {
        AdminGuard.isDashboardAccessAllowed(context)
    }
    val networkStatus by UserPreferencesManager.networkStatus.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentAudioTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isAudioPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    var quickPromptText by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    val recorderHelper = remember { AudioRecorderHelper(context) }

    var selectedSector by remember { mutableIntStateOf(0) }
    var selectedCity by remember { mutableStateOf(PrayerCalculationHelper.getSavedCity(context)) }
    var showCityDialog by remember { mutableStateOf(false) }

    val detectCurrentCityOnce: () -> Unit = {
        scope.launch {
            val hasFinePermission =
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val detectedCity = runCatching {
                withContext(Dispatchers.IO) {
                    PrayerLocationProvider.detectCurrentCity(context, hasFinePermission)
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            if (detectedCity != null) {
                selectedCity = detectedCity
                PrayerCalculationHelper.updateSelectedCity(context, detectedCity)
                showCityDialog = true
                Toast.makeText(
                    context,
                    "تم تحديد موقعك: ${detectedCity.nameAr}. أبقِه أو اختر مدينة أخرى.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "تعذر تحديد الموقع. تحقق من تفعيل الموقع وحاول مجددًا أو اختر المدينة يدويًا.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            detectCurrentCityOnce()
        } else {
            Toast.makeText(
                context,
                "لم يُمنح إذن الموقع. يمكنك اختيار المدينة يدويًا من قسم مواقيت الصلاة.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val openCitySelector: () -> Unit = {
        showCityDialog = true
        val hasLocationPermission =
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission) {
            detectCurrentCityOnce()
        } else {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val calculatedPrayerTimes = remember(currentTimeMillis, selectedCity) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        PrayerCalculationHelper.calculatePrayerTimesForUser(context, cal, selectedCity)
    }

    val liveNotifications by AppNotificationService.observeNotifications(context).collectAsState()
    val unreadNotifications = liveNotifications.count { !it.isRead }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val ok = recorderHelper.startRecording()
            if (ok) {
                isRecordingVoice = true
                Toast.makeText(context, Translator.tr("بدأ الالتقاط الصوتي... تحدث بفكرتك"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, Translator.tr("تعذر بدء الميكروفون"), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, Translator.tr("يلزم إذن الميكروفون للتسجيل الصوتي"), Toast.LENGTH_SHORT).show()
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
                    isGuest = isGuest,
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
                    onMushafReader = onMushafReader,
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
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
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
                                    val audioPlayIconColor by animateColorAsState(
                                        targetValue = if (isAudioPlaying) theme.success else theme.primary,
                                        animationSpec = tween(durationMillis = 300),
                                        label = "audio_play_color"
                                    )
                                    IconButton(
                                        onClick = { AudioPlaybackManager.togglePlayState(context) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            if (isAudioPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                            contentDescription = if (isAudioPlaying) "إيقاف مؤقت" else "تشغيل",
                                            tint = audioPlayIconColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    bottomBar()
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                SpatialCosmicBackground()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    SpatialHudTopBar(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onViewSettings = onViewSettings,
                        onViewNotifications = onViewNotifications,
                        onViewProfile = onViewProfile,
                        onDeveloperDashboard = onDeveloperDashboard,
                        onViewDownloads = onViewDownloads,
                        isAdmin = isAdmin,
                        networkStatus = networkStatus,
                        unreadNotifications = unreadNotifications
                    )

                    SpatialHudStatsHeader(
                        currentTimeMillis = currentTimeMillis,
                        selectedCity = selectedCity,
                        onLocateAndChooseCity = {
                            val hasLocationPermission =
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasLocationPermission) {
                                detectCurrentCityOnce()
                            } else {
                                locationPermissionLauncher.launch(
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            }
                        }
                    )

                    QabasSmartTipsBar(
                        isDeveloper = isDeveloper,
                        onNavigateToDeveloperDashboard = onDeveloperDashboard
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SpatialSpiritualBeacon(
                        prayerTimes = calculatedPrayerTimes,
                        selectedCity = selectedCity,
                        onOpenCitySelector = openCitySelector,
                        onPrayerTimes = onPrayerTimes,
                        onHadithStudio = onHadithStudio,
                        onAzkar = onAzkar,
                        isDeveloper = isDeveloper
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // بطاقة اليوم + أكمل من حيث توقفت + بحث عام (الدفعة 3).
                    val todayState = rememberHomeTodayState()
                    HomeTodayCard(
                        state = todayState,
                        onOpenPrayerTimes = onPrayerTimes,
                        onOpenAzkar = onAzkar,
                        onOpenMishkatHub = onMishkatHub,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HomeContinueRow(
                        state = todayState,
                        recentProjects = recentProjects,
                        onOpenProject = onOpenProject,
                        onOpenMishkatHub = onMishkatHub,
                        onOpenAzkar = onAzkar,
                        onOpenMushaf = onOpenMushaf,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HomeGlobalSearch(
                        onOpenQuranVerse = onOpenQuranVerse,
                        onOpenHadith = onOpenHadith,
                        onOpenLibraryBook = onOpenLibraryBook,
                        onOpenReel = onOpenReel,
                        onOpenCreator = onOpenCreator,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SpatialWarpCreationHub(
                        quickPromptText = quickPromptText,
                        onQuickPromptChange = { quickPromptText = it },
                        isRecordingVoice = isRecordingVoice,
                        onStartVoice = { micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        onStopVoice = {
                            isRecordingVoice = false
                            val file = recorderHelper.stopRecording()
                            if (file != null && file.exists() && file.length() > 0L) {
                                Toast.makeText(context, Translator.tr("تم التقاط التسجيل بنجاح! جاري بدء المشروع..."), Toast.LENGTH_SHORT).show()
                                onNewProjectWithAudio(null, file.absolutePath)
                            }
                        },
                        onIgnite = {
                            if (isRecordingVoice) {
                                Toast.makeText(context, Translator.tr("أوقف التسجيل أولاً قبل بدء المشروع"), Toast.LENGTH_SHORT).show()
                            } else if (quickPromptText.isNotBlank()) {
                                onNewProject(quickPromptText.trim())
                            } else {
                                onStudioGateway()
                            }
                        },
                        isDeveloper = isDeveloper
                    )

                    if (onRestoreWorkspaceDraft != null) {
                        val autoSaveStatus by WorkspaceAutoSaveManager.status.collectAsState()
                        if (autoSaveStatus.hasRecoverableDraft) {
                            Spacer(modifier = Modifier.height(12.dp))
                            SpatialDraftRestoreCard(
                                snippet = autoSaveStatus.draftSnippet,
                                onRestore = onRestoreWorkspaceDraft,
                                onDismiss = { scope.launch { WorkspaceAutoSaveManager.clearAiDraft() } }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SpatialSectorTabs(
                        selectedSector = selectedSector,
                        onSectorSelected = { selectedSector = it },
                        recentCount = recentProjects.size
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AnimatedContent(
                        targetState = selectedSector,
                        transitionSpec = {
                            AppAnimationManager.createSectorTransitionSpec(
                                initialSector = initialState,
                                targetSector = targetState,
                                reducedMotion = reducedMotion
                            )
                        },
                        label = "SectorContentAnimation"
                    ) { sector ->
                        when (sector) {
                            0 -> SpatialStudiosSector(
                                onStudioGateway = onStudioGateway,
                                onReels = onReels,
                                onProEditor = onProEditor,
                                onMoneyPrinterTurbo = onMoneyPrinterTurbo,
                                onVoiceStudio = onVoiceStudio,
                                onPhotoStudio = onPhotoStudio,
                                onYouTubeStudio = onYouTubeStudio,
                                onTeleprompter = onTeleprompter,
                                onVideoStyleCloner = onVideoStyleCloner,
                                onStyleSelection = onStyleSelection,
                                onMediaBrowser = onMediaBrowser,
                                isDeveloper = isDeveloper
                            )
                            1 -> SpatialIslamicSuiteSector(
                                onHadithStudio = onHadithStudio,
                                onIslamicLibrary = onIslamicLibrary,
                                onAzkar = onAzkar,
                                onPrayerTimes = onPrayerTimes,
                                onScholarBiographies = onScholarBiographies,
                                onKnowledgeHub = onKnowledgeHub,
                                isDeveloper = isDeveloper
                            )
                            2 -> SpatialAiEnginesSector(
                                onAiAssistant = onAiAssistant,
                                onContentGuard = onContentGuard,
                                onAudioLibrary = onAudioLibrary,
                                onNavigateToTasteProfile = onNavigateToTasteProfile,
                                onLeaderboard = onLeaderboard,
                                onRewards = onRewards,
                                onPremiumUpgrade = onPremiumUpgrade,
                                onViewApiDocs = onViewApiDocs,
                                onDeveloperDashboard = onDeveloperDashboard,
                                isAdmin = isAdmin,
                                isDeveloper = isDeveloper
                            )
                            3 -> SpatialProjectsSector(
                                recentProjects = recentProjects,
                                onOpenProject = onOpenProject,
                                onViewProjects = onViewProjects,
                                onNewProject = { onNewProject(null) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AppRealVersionFooterBadge(
                        onViewDownloads = onViewDownloads
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showCityDialog) {
        PrayerCitySelectionDialog(
            currentSelectedCity = selectedCity,
            onDismiss = { showCityDialog = false },
            onCitySelected = { newCity ->
                selectedCity = newCity
                showCityDialog = false
                Toast.makeText(context, "تم ضبط مواقيت الصلاة لمدينة ${newCity.nameAr} (${newCity.countryAr})", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun SpatialCosmicBackground() {
    val theme = QabasTheme.current
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    theme.primary.copy(alpha = if (theme.isDark) 0.08f else 0.05f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.10f),
                radius = size.width * 0.8f
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width * 0.85f, size.height * 0.10f)
        )
    }
}

@Composable
private fun SpatialHudTopBar(
    onOpenDrawer: () -> Unit,
    onViewSettings: () -> Unit,
    onViewNotifications: () -> Unit,
    onViewProfile: () -> Unit,
    onDeveloperDashboard: () -> Unit,
    onViewDownloads: () -> Unit,
    isAdmin: Boolean,
    networkStatus: ConnectivityObserver.Status,
    unreadNotifications: Int = 0
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenDrawer)
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.primary.copy(alpha = if (theme.isDark) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "فتح القائمة",
                    tint = theme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "خادم المسلم",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val netColor = if (networkStatus == ConnectivityObserver.Status.Available) theme.success else theme.destructive
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(netColor)
                    )
                }
                Text(
                    text = "استوديو الإنتاج الدعوي",
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = theme.cardSurface,
            border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.45f)),
            shadowElevation = if (theme.isDark) 0.dp else 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAdmin) {
                    IconButton(
                        onClick = onDeveloperDashboard,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = "لوحة المطور",
                            tint = theme.destructive,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onViewDownloads,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("spatial_hud_downloads_button")
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = "مركز التنزيلات",
                        tint = theme.accent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onViewNotifications,
                    modifier = Modifier.size(48.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge(
                                    containerColor = theme.destructive,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = if (unreadNotifications > 99) "99+" else unreadNotifications.toString(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = "الإشعارات",
                            tint = theme.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onViewProfile,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.PersonOutline,
                        contentDescription = "الملف الشخصي",
                        tint = theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onViewSettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "الإعدادات",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpatialSpiritualBeacon(
    prayerTimes: CalculatedPrayerTimes,
    selectedCity: CityLocation,
    onOpenCitySelector: () -> Unit,
    onPrayerTimes: () -> Unit,
    onHadithStudio: () -> Unit,
    onAzkar: () -> Unit,
    isDeveloper: Boolean = false
) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onPrayerTimes
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mosque, contentDescription = null, tint = theme.primary, modifier = Modifier.size(21.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = PrayerCalculationHelper.nextEventTitle(prayerTimes.nextPrayerName),
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${prayerTimes.nextPrayerTimeFormatted})",
                                color = theme.primary,
                                fontFamily = RobotoMonoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "متبقي:",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = formatRemainingTime(prayerTimes.millisUntilNextPrayer),
                                    color = theme.primary,
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "• ${selectedCity.nameAr}",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    onClick = onOpenCitySelector,
                    shape = RoundedCornerShape(10.dp),
                    color = theme.secondarySurface.copy(alpha = 0.7f),
                    modifier = Modifier.heightIn(min = 38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedCity.nameAr,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Surface(
                color = theme.secondarySurface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "اتجاه القبلة: ${prayerTimes.qiblaDirectionDeg.toInt()}°",
                            color = theme.textPrimary,
                            fontSize = 11.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "المسافة إلى مكة: ${prayerTimes.distanceToMakkahKm} كم",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = NotoSansFont
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDeveloper) {
                    Surface(
                        onClick = onHadithStudio,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = theme.primary.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoStories, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("الحديث الشريف", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                Surface(
                    onClick = onAzkar,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFF2D55).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF2D55), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الأذكار وحصن المسلم", color = Color(0xFFFF2D55), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpatialDraftRestoreCard(
    snippet: String,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
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
                    Text(snippet.ifBlank { "استعد تعديلات الفيديو السابقة" }, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRestore) {
                    Text("استعادة", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "تجاهل", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SpatialSectorTabs(
    selectedSector: Int,
    onSectorSelected: (Int) -> Unit,
    recentCount: Int
) {
    val theme = QabasTheme.current
    val sectors = listOf(
        Triple(0, "الاستوديوهات", Icons.Default.VideoCameraBack),
        Triple(1, "مِشكاة الهُدى", Icons.Default.Mosque),
        Triple(2, "الأدوات والذكاء", Icons.Default.Psychology),
        Triple(3, "مشاريعي ($recentCount)", Icons.Default.FolderSpecial)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sectors) { (index, title, icon) ->
            val isSelected = selectedSector == index
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) theme.primary else theme.cardSurface,
                animationSpec = tween(220),
                label = "sector_bg_color"
            )
            Surface(
                onClick = { onSectorSelected(index) },
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                border = if (!isSelected) BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f)) else null,
                shadowElevation = if (isSelected && !theme.isDark) 2.dp else 0.dp,
                modifier = Modifier.heightIn(min = 42.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) theme.onPrimary else theme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = if (isSelected) theme.onPrimary else theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SpatialHudStatsHeader(
    currentTimeMillis: Long,
    selectedCity: CityLocation,
    onLocateAndChooseCity: () -> Unit
) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    }
    val savedDisplayName = (
        prefs.getString("user_display_name", null)
            ?: CloudServices.Auth.currentUser.value?.displayName
    )?.trim()?.takeIf { it.isNotBlank() } ?: "مستخدم خادم المسلم"

    val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    val hourFormat = remember { java.text.SimpleDateFormat("HH", Locale.getDefault()) }
    val minuteFormat = remember { java.text.SimpleDateFormat("mm", Locale.getDefault()) }
    val secondFormat = remember { java.text.SimpleDateFormat("ss", Locale.getDefault()) }
    val clockHour = hourFormat.format(calendar.time)
    val clockMinute = minuteFormat.format(calendar.time)
    val clockSecond = secondFormat.format(calendar.time)
    val dateFormat = remember { java.text.SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")) }
    val formattedDate = dateFormat.format(calendar.time)

    val isAm = calendar.get(Calendar.AM_PM) == Calendar.AM
    val amPmLabel = if (isAm) "صباحًا" else "مساءً"

    AppleCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        cornerRadius = 18.dp
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = savedDisplayName.firstOrNull()?.toString() ?: "ق",
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مرحبًا بك",
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = savedDisplayName,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onLocateAndChooseCity,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "تحديد موقعي تلقائيًا",
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = selectedCity.nameAr,
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 76.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val toolsEngine = remember(context) { MuslimToolsEngine(context) }
                val hijriComp = remember(currentTimeMillis) { toolsEngine.getHijriComponents(currentTimeMillis) }

                Surface(
                    modifier = Modifier
                        .weight(1.12f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(14.dp),
                    color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.72f else 0.55f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                            Text(
                                text = "التقويم الهجري",
                                color = theme.primary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = theme.cardSurface,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = hijriComp.day,
                                        color = theme.textPrimary,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${hijriComp.monthName} ${hijriComp.year}",
                                    color = theme.textPrimary,
                                    fontFamily = AmiriFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedDate,
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(0.88f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(14.dp),
                    color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.72f else 0.55f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الوقت الآن",
                                color = theme.primary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = amPmLabel,
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = theme.cardSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$clockHour:$clockMinute",
                                        color = theme.textPrimary,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = ":$clockSecond",
                                        color = theme.primary,
                                        fontFamily = RobotoMonoFont,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRemainingTime(millis: Long): String {
    val totalSec = (millis / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return String.format("%02d:%02d:%02d", h, m, s)
}

/** يفتح سلسلة السياق للوصول إلى الـ Activity المضيفة لإغلاق التطبيق بشكل موثوق. */
fun Context.findHostActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
