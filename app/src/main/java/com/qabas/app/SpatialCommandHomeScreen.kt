package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.HudCornerDecor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.sin

/**
 * 🛸 شاشة القيادة الفضائية الكوانتية (Spatial HUD Command Center Home Screen)
 * تجربة مستخدم مستقبلية خارقة تعتمد على الزجاج السيبراني، رادار التيليمتري المباشر،
 * ومصفوفة القطاعات المتقاطعة مع وصول فوري وشامل لجميع خدمات الاستوديو والوحيين والذكاء الاصطناعي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialCommandHomeScreen(
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
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val isAdmin = prefs.getBoolean("is_admin", false)
    val isDeveloper = remember(context) { AdminGuard.isDashboardAccessAllowed(context) }
    val networkStatus by UserPreferencesManager.networkStatus.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    // مشغل الصوتيات المصغر
    val currentAudioTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isAudioPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    // مدخلات التوليد السريع والصوتي
    var quickPromptText by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    val recorderHelper = remember { AudioRecorderHelper(context) }

    // القطاع المختار (0: الاستوديو والإنتاج, 1: مِشكاة الهُدى والعلوم الشرعية, 2: الأدوات والذكاء, 3: مشاريعي)
    var selectedSector by remember { mutableIntStateOf(0) }

    // إدارة المدينة المختارة لمواقيت الصلاة
    var selectedCity by remember { mutableStateOf(PrayerCalculationHelper.getSavedCity(context)) }
    var showCityDialog by remember { mutableStateOf(false) }

    // حساب مواقيت الصلاة الحقيقية والوقت المتبقي
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val calculatedPrayerTimes = remember(currentTimeMillis, selectedCity) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        PrayerCalculationHelper.calculatePrayerTimes(cal, selectedCity, selectedCity.defaultMethod)
    }

    // إذن الميكروفون
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val ok = recorderHelper.startRecording()
            if (ok) {
                isRecordingVoice = true
                Toast.makeText(context, Translator.tr("بدأ الالتقاط الصوتي الكوانتي... تحدث بفكرتك 🎙️"), Toast.LENGTH_SHORT).show()
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
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Translator.tr("إيقاف تشغيل مصفوفة قبس"), color = primaryColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(Translator.tr("هل تود إغلاق مركز القيادة وتطبيق قبس ستوديو؟"), color = TextPrimary, fontFamily = CairoFont, fontSize = 13.5.sp)
            },
            confirmButton = {
                Button(
                    onClick = { (context as? android.app.Activity)?.finishAffinity() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = CutCornerShape(8.dp)
                ) {
                    Text(Translator.tr("إغلاق التطبيق 🛑"), color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(Translator.tr("إلغاء"), color = TextSecondary, fontFamily = CairoFont)
                }
            },
            containerColor = CardSurface,
            shape = CutCornerShape(12.dp)
        )
    }

    // الدرج الجانبي الموحد والشامل
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DeepSlate,
                drawerContentColor = TextPrimary,
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
                    onReels = onReels,
                    onProEditor = onProEditor,
                    onMoneyPrinterTurbo = onMoneyPrinterTurbo,
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
                    onNavigateToRules = onRules
                )
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = DeepSlate,
            bottomBar = {
                Column {
                    // شريط تشغيل الصوتيات المصغر الزجاجي
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
                                shape = CutCornerShape(12.dp),
                                color = CardSurface.copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(primaryColor, accentColor))),
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
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(primaryColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                        Text(track.artist, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp, maxLines = 1)
                                    }
                                    val audioPlayIconColor by animateColorAsState(
                                        targetValue = if (isAudioPlaying) Color(0xFF10B981) else primaryColor,
                                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                                        label = "audio_play_color"
                                    )
                                    IconButton(
                                        onClick = {
                                            AudioPlaybackManager.togglePlayState(context)
                                        }
                                    ) {
                                        Icon(
                                            if (isAudioPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                            contentDescription = null,
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
                // الخلفية الكوانتية مع جزيئات النور السابحة
                SpatialCosmicBackground()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. شريط التحكم الفضائي العلوي (Spatial HUD Header & Telemetry)
                    SpatialHudTopBar(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onViewSettings = onViewSettings,
                        onViewNotifications = onViewNotifications,
                        onViewProfile = onViewProfile,
                        onDeveloperDashboard = onDeveloperDashboard,
                        isAdmin = isAdmin,
                        networkStatus = networkStatus
                    )

                    // 1.1 Holographic Dynamic Greeting & System Telemetry
                    SpatialHudStatsHeader(
                        currentTimeMillis = currentTimeMillis,
                        selectedCity = selectedCity,
                        onOpenCitySelector = { showCityDialog = true }
                    )

                    // 1.2 Dynamic System Ticker (Quran, Sunnah & Developer Pro Tips)
                    QabasSmartTipsBar(
                        isDeveloper = isDeveloper,
                        onNavigateToDeveloperDashboard = onDeveloperDashboard
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. منارة الوحيين والبوصلة اليومية (Holographic Spiritual Beacon)
                    SpatialSpiritualBeacon(
                        prayerTimes = calculatedPrayerTimes,
                        selectedCity = selectedCity,
                        onOpenCitySelector = { showCityDialog = true },
                        onPrayerTimes = onPrayerTimes,
                        onQuranHub = onQuranHub,
                        onHadithStudio = onHadithStudio,
                        onAzkar = onAzkar,
                        isDeveloper = isDeveloper
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. بوابة التوليد والإنتاج الفضائي (The Quantum Warp Portal)
                    SpatialWarpCreationHub(
                        quickPromptText = quickPromptText,
                        onQuickPromptChange = { quickPromptText = it },
                        isRecordingVoice = isRecordingVoice,
                        onStartVoice = { micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        onStopVoice = {
                            isRecordingVoice = false
                            val file = recorderHelper.stopRecording()
                            if (file != null && file.exists()) {
                                Toast.makeText(context, Translator.tr("تم التقاط التسجيل بنجاح! جاري بدء المشروع... ✨"), Toast.LENGTH_SHORT).show()
                                onNewProject(null)
                            }
                        },
                        onIgnite = {
                            if (quickPromptText.isNotBlank()) {
                                onNewProject(quickPromptText.trim())
                            } else {
                                onStudioGateway()
                            }
                        },
                        isDeveloper = isDeveloper
                    )

                    // مسودة العمل المحفوظة إن وجدت
                    if (onRestoreWorkspaceDraft != null) {
                        val autoSaveStatus by WorkspaceAutoSaveManager.status.collectAsState()
                        if (autoSaveStatus.hasRecoverableDraft) {
                            Spacer(modifier = Modifier.height(14.dp))
                            SpatialDraftRestoreCard(
                                snippet = autoSaveStatus.draftSnippet,
                                onRestore = onRestoreWorkspaceDraft,
                                onDismiss = { scope.launch { WorkspaceAutoSaveManager.clearAiDraft() } }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4. محدد القطاعات الفضائية التفاعلي (Spatial Sector Selector)
                    SpatialSectorTabs(
                        selectedSector = selectedSector,
                        onSectorSelected = { selectedSector = it },
                        recentCount = recentProjects.size
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. محتوى القطاع النشط بجميع الخدمات الدقيقة
                    AnimatedContent(
                        targetState = selectedSector,
                        transitionSpec = {
                            fadeIn(tween(260)) + slideInHorizontally { it / 5 } togetherWith
                                    fadeOut(tween(180)) + slideOutHorizontally { -it / 5 }
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
                                onQuranHub = onQuranHub,
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
                }
            }
        }
    }

    // نافذة اختيار الدولة والمدينة لمواقيت الصلاة
    if (showCityDialog) {
        PrayerCitySelectionDialog(
            currentSelectedCity = selectedCity,
            onDismiss = { showCityDialog = false },
            onCitySelected = { newCity ->
                selectedCity = newCity
                showCityDialog = false
                Toast.makeText(context, "تم ضبط مواقيت الصلاة لمدينة ${newCity.nameAr} (${newCity.countryAr}) 🕋", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * خلفية فضائية كوانتية بنجوم وجسيمات متحركة
 */
@Composable
private fun SpatialCosmicBackground() {
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()
    val isCyberHud = QabasTheme.current.isCyberHud

    val infiniteTransition = rememberInfiniteTransition(label = "SpaceStars")
    val starPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starPhase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepX = 40.dp.toPx()
        val stepY = 40.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // HUD Grid
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

        // تدرج شعاعي هولوغرافي في الزاوية العلوية
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = if (isCyberHud) 0.12f else 0.08f),
                    accentColor.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.15f),
                radius = size.width * 0.75f
            ),
            radius = size.width * 0.75f,
            center = Offset(size.width * 0.85f, size.height * 0.15f)
        )

        // تدرج في الزاوية السفلية المعاكسة
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.07f),
                    primaryColor.copy(alpha = 0.03f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.15f, size.height * 0.85f),
                radius = size.width * 0.65f
            ),
            radius = size.width * 0.65f,
            center = Offset(size.width * 0.15f, size.height * 0.85f)
        )

        // جزيئات النجوم الكوانتية
        val stars = listOf(
            Triple(0.12f, 0.08f, 1.4f),
            Triple(0.85f, 0.12f, 2.0f),
            Triple(0.35f, 0.22f, 1.2f),
            Triple(0.72f, 0.38f, 1.8f),
            Triple(0.18f, 0.52f, 1.5f),
            Triple(0.88f, 0.68f, 2.2f),
            Triple(0.45f, 0.78f, 1.3f),
            Triple(0.25f, 0.92f, 1.6f)
        )

        stars.forEachIndexed { i, (xRel, yRel, baseR) ->
            val dynamicAlpha = 0.25f + 0.55f * sin(starPhase + i * 1.3f).coerceIn(0f, 1f)
            val posX = size.width * xRel
            val posY = size.height * yRel
            drawCircle(
                color = if (i % 2 == 0) primaryColor.copy(alpha = dynamicAlpha) else accentColor.copy(alpha = dynamicAlpha),
                radius = baseR.dp.toPx(),
                center = Offset(posX, posY)
            )
        }
    }
}

/**
 * شريط القيادة الفضائي العلوي
 */
@Composable
private fun SpatialHudTopBar(
    onOpenDrawer: () -> Unit,
    onViewSettings: () -> Unit,
    onViewNotifications: () -> Unit,
    onViewProfile: () -> Unit,
    onDeveloperDashboard: () -> Unit,
    isAdmin: Boolean,
    networkStatus: ConnectivityObserver.Status
) {
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // الشعار والدرج الجانبي
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable { onOpenDrawer() }
        ) {
            CyberneticAnimatedLogo(
                size = 36.dp,
                showOrbitalHud = false,
                showCosmicGlow = false,
                showLightSweep = true,
                showLevitation = false,
                showCornerBrackets = false,
                showParticles = false,
                onClick = onOpenDrawer
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "قَبَس ستوديو",
                        color = primaryColor,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = primaryColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "HUD ✦",
                            color = primaryColor,
                            fontFamily = RobotoMonoFont,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(5.dp))

                    // مؤشر حالة الشبكة
                    val (netColor, netLabel) = when (networkStatus) {
                        ConnectivityObserver.Status.Available -> Color(0xFF10B981) to "ONLINE"
                        else -> Color(0xFFEF4444) to "OFFLINE"
                    }

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(netColor)
                    )
                }
            }
        }

        // أزرار التحكم السريع (Spatial Actions Capsule)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardSurface.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAdmin) {
                    IconButton(
                        onClick = onDeveloperDashboard,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = "Developer",
                            tint = accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onViewNotifications,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = primaryColor,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onViewProfile,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.PersonOutline,
                        contentDescription = "Profile",
                        tint = primaryColor,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onViewSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = primaryColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/**
 * منارة الوحيين والبوصلة الفضائية (Spatial Spiritual Beacon)
 */
@Composable
private fun SpatialSpiritualBeacon(
    prayerTimes: CalculatedPrayerTimes,
    selectedCity: CityLocation,
    onOpenCitySelector: () -> Unit,
    onPrayerTimes: () -> Unit,
    onQuranHub: () -> Unit,
    onHadithStudio: () -> Unit,
    onAzkar: () -> Unit,
    isDeveloper: Boolean = false
) {
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clickable { onPrayerTimes() },
        shape = CutCornerShape(12.dp),
        backgroundColor = CardSurface.copy(alpha = 0.85f),
        borderColor = primaryColor.copy(alpha = 0.4f),
    ) {
        HudCornerDecor(modifier = Modifier.matchParentSize(), color = primaryColor.copy(alpha = 0.15f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // الصف العلوي: رادار الصلاة القادمة والوقت المتبقي
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
                            .size(38.dp)
                            .clip(CutCornerShape(8.dp))
                            .background(primaryColor.copy(alpha = 0.12f))
                            .border(1.dp, primaryColor.copy(alpha = 0.35f), CutCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mosque, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "الصلاة القادمة: ${prayerTimes.nextPrayerName}",
                                color = primaryColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${prayerTimes.nextPrayerTimeFormatted})",
                                color = accentColor,
                                fontFamily = RobotoMonoFont,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "متبقي: ${formatRemainingTime(prayerTimes.millisUntilNextPrayer)} • 📍 ${selectedCity.nameAr}",
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 10.5.sp,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    onClick = onOpenCitySelector,
                    shape = CutCornerShape(6.dp),
                    color = primaryColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = primaryColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${selectedCity.nameAr} ▾",
                            color = primaryColor,
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // شريط بوصلة القبلة والمسافة لمكة المكرمة
            Surface(
                color = Color(0xFF09121E).copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, Color(0xFF1E2D4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = GoldSecondary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "اتجاه القبلة: ${prayerTimes.qiblaDirectionDeg.toInt()}°",
                            color = GoldSecondary,
                            fontSize = 10.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "🕋 بعد مكة: ${prayerTimes.distanceToMakkahKm} كم",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontFamily = NotoSansFont
                    )
                }
            }

            // الصف السفلي: أزرار سريعة للقرآن والحديث والأذكار مقسمة بمرونة 1:1:1 لتفادي أي انضغاط أو التفاف عمودي للنص
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onQuranHub,
                    modifier = Modifier.weight(1f),
                    shape = CutCornerShape(6.dp),
                    color = primaryColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("القرآن", color = primaryColor, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                if (isDeveloper) {
                    Surface(
                        onClick = onHadithStudio,
                        modifier = Modifier.weight(1f),
                        shape = CutCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoStories, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("الحديث", color = accentColor, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                Surface(
                    onClick = onAzkar,
                    modifier = Modifier.weight(1f),
                    shape = CutCornerShape(6.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("الأذكار", color = Color(0xFFF59E0B), fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * منصة الإطلاق والتوليد الفضائي الكوانتي (The Quantum Warp Portal)
 */
@Composable
private fun SpatialWarpCreationHub(
    quickPromptText: String,
    onQuickPromptChange: (String) -> Unit,
    isRecordingVoice: Boolean,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onIgnite: () -> Unit,
    isDeveloper: Boolean = false
) {
    if (!isDeveloper) return
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    val infiniteTransition = rememberInfiniteTransition(label = "WarpPulse")
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderGlow"
    )

    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = CutCornerShape(16.dp),
        backgroundColor = CardSurface.copy(alpha = 0.92f),
        borderColor = primaryColor.copy(alpha = borderGlow),
    ) {
        HudCornerDecor(modifier = Modifier.matchParentSize(), color = primaryColor.copy(alpha = 0.15f))
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // العنوان وشارة الذكاء الاصطناعي
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.15f))
                            .border(1.dp, primaryColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "محرك التوليد الفضائي",
                            color = primaryColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "حوّل أي فكرة أو صوت إلى فيديو سينمائي متكامل",
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = primaryColor.copy(alpha = 0.15f),
                    shape = CutCornerShape(6.dp),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "AI ENGINE 2.5",
                        color = primaryColor,
                        fontFamily = RobotoMonoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // حقل النص الذكي مع زر التسجيل الصوتي
            OutlinedTextField(
                value = quickPromptText,
                onValueChange = onQuickPromptChange,
                placeholder = {
                    Text(
                        text = if (isRecordingVoice) "جاري التسجيل الصوتي... تحدث بفكرتك 🎙️"
                        else "اكتب فكرة الفيديو، موضوع دعوي، أو آية وحديث...",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = CairoFont
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (isRecordingVoice) onStopVoice() else onStartVoice()
                        }
                    ) {
                        Icon(
                            imageVector = if (isRecordingVoice) Icons.Default.StopCircle else Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = if (isRecordingVoice) Color(0xFFEF4444) else primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                shape = CutCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    color = TextPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DeepSlate.copy(alpha = 0.7f),
                    unfocusedContainerColor = DeepSlate.copy(alpha = 0.7f),
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = primaryColor.copy(alpha = 0.25f)
                ),
                maxLines = 3
            )

            // قوالب الانطلاق السريعة
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val presets = listOf(
                    "🎬 ريلز دعوي خاطف",
                    "📖 قصة قرآنية مؤثرة",
                    "✨ شرح حديث نبوي",
                    "🌙 سلسلة رمضانية",
                    "🎙️ حكمة وتأمل قصير"
                )
                items(presets) { preset ->
                    Surface(
                        onClick = {
                            val clean = preset.substringAfter(" ")
                            onQuickPromptChange("صناعة فيديو عن: $clean")
                        },
                        shape = CutCornerShape(8.dp),
                        color = DeepSlate.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = preset,
                            color = TextPrimary.copy(alpha = 0.9f),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // زر الإطلاق الفضائي الخارق
            Button(
                onClick = onIgnite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = CutCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (quickPromptText.isNotBlank()) "إطلاق المعالجة الفضائية 🚀" else "فتح استوديو الإنتاج الشامل ✨",
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

/**
 * بطاقة استعادة مسودة العمل الذكية
 */
@Composable
private fun SpatialDraftRestoreCard(
    snippet: String,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = qabasPrimary()

    Surface(
        shape = CutCornerShape(12.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CutCornerShape(6.dp))
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("مسودة محفوظة في ذاكرة العمل 💾", color = primaryColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(snippet.ifBlank { "استعد تعديلات الفيديو السابقة" }, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRestore) {
                    Text("استعادة", color = primaryColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "تجاهل", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * محدد القطاعات الفضائية التفاعلي (Spatial Sector Tabs)
 */
@Composable
private fun SpatialSectorTabs(
    selectedSector: Int,
    onSectorSelected: (Int) -> Unit,
    recentCount: Int
) {
    val primaryColor = qabasPrimary()

    val sectors = listOf(
        Triple(0, "الاستوديوهات", Icons.Default.VideoCameraBack),
        Triple(1, "مِشكاة الهُدى", Icons.Default.Mosque),
        Triple(2, "الأدوات والذكاء", Icons.Default.Psychology),
        Triple(3, "مشاريعي (${recentCount})", Icons.Default.FolderSpecial)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sectors) { (index, title, icon) ->
            val isSelected = selectedSector == index
            Surface(
                onClick = { onSectorSelected(index) },
                shape = CutCornerShape(8.dp),
                color = if (isSelected) primaryColor else CardSurface,
                border = BorderStroke(1.dp, if (isSelected) primaryColor else primaryColor.copy(alpha = 0.2f)),
                shadowElevation = if (isSelected) 6.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) DeepSlate else primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = if (isSelected) DeepSlate else TextPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * قطاع 1: الاستوديو والإنتاج المرئي (Creative Studios)
 */
@Composable
private fun SpatialStudiosSector(
    onStudioGateway: () -> Unit,
    onReels: () -> Unit = {},
    onProEditor: () -> Unit,
    onMoneyPrinterTurbo: () -> Unit,
    onVoiceStudio: () -> Unit,
    onPhotoStudio: () -> Unit,
    onYouTubeStudio: () -> Unit,
    onTeleprompter: () -> Unit,
    onVideoStyleCloner: () -> Unit,
    onStyleSelection: () -> Unit,
    onMediaBrowser: () -> Unit = {},
    isDeveloper: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hero Section: Digital Master Engine
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clickable { onStudioGateway() },
            shape = CutCornerShape(16.dp),
            color = Color.Black,
            border = BorderStroke(1.dp, qabasPrimary().copy(alpha = 0.4f))
        ) {
            Box {
                coil.compose.AsyncImage(
                    model = R.drawable.spatial_hud_gateway_refined_1790877953352,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alpha = 0.5f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(16.dp)
                ) {
                    Surface(
                        color = GoldPrimary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "MASTER ENGINE ✦",
                            color = DeepSlate,
                            fontFamily = RobotoMonoFont,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "استوديو التحف الموحد",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Text(
                        "ابدأ مشروعك الدعوي بنظام Higgsfield AI الفائق",
                        color = qabasPrimary(),
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ريلز ومجتمع قبس الإيماني
        SpatialHubCard(
            title = "ريلز ومجتمع قبس الإيماني 📱",
            subtitle = "مشاهدة ونشر مقاطع الريلز القصيرة (9:16) والتفاعل مع مجتمع صناع الأثر",
            icon = Icons.Default.SlowMotionVideo,
            badge = "REELS TIKTOK ✦",
            color = GoldPrimary,
            modifier = Modifier.fillMaxWidth(),
            onClick = onReels
        )

        // الاستوديو الموحد ومحرك Higgsfield AI الفائق
        SpatialHubCard(
            title = "استوديو التحف الموحد & Higgsfield AI 🎬",
            subtitle = "توليد فيديوهات دعوية متكاملة (فيديو سينمائي بالذكاء + تعليق صوتي + مؤثرات ونشر فورياً)",
            icon = Icons.Default.MovieFilter,
            badge = "HIGGSFIELD AI ✦",
            color = Color(0xFFFF5252),
            modifier = Modifier.fillMaxWidth(),
            onClick = onStudioGateway
        )

        // مستودع الوسائط المركزي وسحابة Supabase
        SpatialHubCard(
            title = "مستودع الوسائط وسحابة Supabase 📦",
            subtitle = "تصفح وإدارة كل الملفات المرفوعة والمولّدة بالذكاء (FLUX 8K + صوتيات + ريلز)",
            icon = Icons.Default.PermMedia,
            badge = "CENTRAL HUB",
            color = Color(0xFF10B981),
            modifier = Modifier.fillMaxWidth(),
            onClick = onMediaBrowser
        )

        // بطاقتي الذكاء الاصطناعي الفائق: MoneyPrinter Turbo + VoiceStudio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "MoneyPrinter Turbo ⚡",
                subtitle = "أتمتة إنتاج ريلز وشورتس 9:16 بضغطة زر",
                icon = Icons.Default.Bolt,
                badge = "TURBO AI",
                color = Color(0xFF00F2FE),
                modifier = Modifier.weight(1f),
                onClick = onMoneyPrinterTurbo
            )

            SpatialHubCard(
                title = "VoiceStudio 🎙️",
                subtitle = "استنساخ الأصوات ودبلجة الفيديوهات",
                icon = Icons.Default.GraphicEq,
                badge = "CLONER AI",
                color = Color(0xFFA855F7),
                modifier = Modifier.weight(1f),
                onClick = onVoiceStudio
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "استوديو قبس الشامل",
                subtitle = "البوابة المركزية ومسارات الإنتاج الذكية",
                icon = Icons.Default.AutoAwesome,
                badge = "الرئيسي",
                color = qabasPrimary(),
                modifier = Modifier.weight(1f),
                onClick = onStudioGateway
            )

            SpatialHubCard(
                title = "المحرر اليدوي الاحترافي",
                subtitle = "تايم لاين، مسارات، ومونتاج دقيق",
                icon = Icons.Default.MovieFilter,
                badge = "Pro Editor",
                color = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
                onClick = onProEditor
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "استوديو الصور والمصغرات",
                subtitle = "تصميم أغلفة وبوسترات 4K",
                icon = Icons.Default.Image,
                badge = "Thumbnails",
                color = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
                onClick = onPhotoStudio
            )

            SpatialHubCard(
                title = "ملقن الكاميرا الذكي",
                subtitle = "تسجيل الفيديو مع قراءة النصوص",
                icon = Icons.Default.Videocam,
                badge = "Teleprompter",
                color = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = onTeleprompter
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "مستنسخ الأنماط البصرية",
                subtitle = "استخلاص الأسلوب والمونتاج من الفيديوهات",
                icon = Icons.Default.Style,
                badge = "Style Cloner",
                color = Color(0xFFA855F7),
                modifier = Modifier.weight(1f),
                onClick = onVideoStyleCloner
            )

            SpatialHubCard(
                title = "استوديو يوتيوب والشورتس",
                subtitle = "توليد العناوين، الفصول، والـ SEO",
                icon = Icons.Default.SmartDisplay,
                badge = "YouTube Studio",
                color = Color(0xFFEF4444),
                modifier = Modifier.weight(1f),
                onClick = onYouTubeStudio
            )
        }

        SpatialHubCard(
            title = "مكتبة الأنماط السينمائية",
            subtitle = "اختيار وتخصيص نمط المونتاج والهوية البصرية",
            icon = Icons.Default.Palette,
            badge = "Styles Library",
            color = Color(0xFF06B6D4),
            modifier = Modifier.fillMaxWidth(),
            onClick = onStyleSelection
        )
    }
}

/**
 * قطاع 2: مِشكاة الهُدى والعلوم الشرعية (Mishkat Islamic Suite)
 */
@Composable
private fun SpatialIslamicSuiteSector(
    onQuranHub: () -> Unit,
    onHadithStudio: () -> Unit,
    onIslamicLibrary: () -> Unit,
    onAzkar: () -> Unit,
    onPrayerTimes: () -> Unit,
    onScholarBiographies: () -> Unit,
    onKnowledgeHub: () -> Unit,
    isDeveloper: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "القرآن الكريم والتجويد",
                subtitle = "المصحف المرتل وتدبر الآيات والتفسير",
                icon = Icons.Default.MenuBook,
                badge = "المصحف الشريف",
                color = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = onQuranHub
            )

            if (isDeveloper) {
                SpatialHubCard(
                    title = "استوديو الحديث الشريف",
                    subtitle = "صحيح البخاري ومسلم وبطاقات 4K",
                    icon = Icons.Default.AutoStories,
                    badge = "الأحاديث الصحيحة",
                    color = qabasPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = onHadithStudio
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "الأذكار وحصن المسلم",
                subtitle = "أذكار الصباح والمساء والسبحة الذكية",
                icon = Icons.Default.Favorite,
                badge = "حصن المسلم",
                color = Color(0xFFEC4899),
                modifier = Modifier.weight(1f),
                onClick = onAzkar
            )

            SpatialHubCard(
                title = "مواقيت الصلاة والقبلة",
                subtitle = "حساب فلكي دقيق وبوصلة ثلاثية الأبعاد",
                icon = Icons.Default.Explore,
                badge = "المواقيت الحية",
                color = Color(0xFF0EA5E9),
                modifier = Modifier.weight(1f),
                onClick = onPrayerTimes
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "المكتبة الإسلامية الكبرى",
                subtitle = "أمهات كتب التفسير، الحديث، والعقيدة",
                icon = Icons.Default.LocalLibrary,
                badge = "كتب محققة",
                color = Color(0xFFF97316),
                modifier = Modifier.weight(1f),
                onClick = onIslamicLibrary
            )

            SpatialHubCard(
                title = "سيرة الأكابر وتراجم الأئمة",
                subtitle = "سير أئمة السلف والعلماء والمحدثين",
                icon = Icons.Default.School,
                badge = "تراجم الأئمة",
                color = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f),
                onClick = onScholarBiographies
            )
        }

        SpatialHubCard(
            title = "منارة المعرفة وتزكية النفس",
            subtitle = "مقالات إيمانية وبناء الوعي الدعوي والتزكية",
            icon = Icons.Default.Lightbulb,
            badge = "تزكية ومعرفة",
            color = Color(0xFF14B8A6),
            modifier = Modifier.fillMaxWidth(),
            onClick = onKnowledgeHub
        )
    }
}

/**
 * قطاع 3: الأدوات الذكية ومحركات الذكاء (AI Engines & Growth Tools)
 */
@Composable
private fun SpatialAiEnginesSector(
    onAiAssistant: () -> Unit,
    onContentGuard: () -> Unit,
    onAudioLibrary: () -> Unit,
    onNavigateToTasteProfile: () -> Unit,
    onLeaderboard: () -> Unit,
    onRewards: () -> Unit,
    onPremiumUpgrade: () -> Unit,
    onViewApiDocs: () -> Unit,
    onDeveloperDashboard: () -> Unit,
    isAdmin: Boolean,
    isDeveloper: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isDeveloper) {
                SpatialHubCard(
                    title = "مستشار الذكاء الاصطناعي",
                    subtitle = "توليد سيناريوهات وأفكار إخراجية مبتكرة",
                    icon = Icons.Default.Psychology,
                    badge = "AI Co-pilot",
                    color = qabasPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = onAiAssistant
                )
            }

            SpatialHubCard(
                title = "حارس المحتوى الشرعي",
                subtitle = "تدقيق وفلترة إسلامية لضمان السلامة",
                icon = Icons.Default.Shield,
                badge = "Content Guard",
                color = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = onContentGuard
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "مكتبة الصوتيات والتلاوات",
                subtitle = "تلاوات خاشعة ومؤثرات صوتية مباحة",
                icon = Icons.Default.GraphicEq,
                badge = "Audio Library",
                color = Color(0xFFEC4899),
                modifier = Modifier.weight(1f),
                onClick = onAudioLibrary
            )

            SpatialHubCard(
                title = "الذوق الفني والأسلوب",
                subtitle = "تخصيص الهوية ونمطك المفضل في المونتاج",
                icon = Icons.Default.Tune,
                badge = "Taste Profile",
                color = Color(0xFF6366F1),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasteProfile
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "لوحة الشرف وميدان الحسنات",
                subtitle = "رصيد النقاط والتنافس المبارك في نشر الخير",
                icon = Icons.Default.EmojiEvents,
                badge = "Leaderboard",
                color = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
                onClick = onLeaderboard
            )

            SpatialHubCard(
                title = "سوق الجوائز والمكافآت",
                subtitle = "استبدال النقاط والحسنات بمكافآت حصرية",
                icon = Icons.Default.CardGiftcard,
                badge = "Rewards Hub",
                color = Color(0xFF14B8A6),
                modifier = Modifier.weight(1f),
                onClick = onRewards
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "ترقية الحساب المميز VIP",
                subtitle = "تصدير 4K وخوادم سريعة وميزات حصرية",
                icon = Icons.Default.WorkspacePremium,
                badge = "VIP Upgrade",
                color = qabasPrimary(),
                modifier = Modifier.weight(1f),
                onClick = onPremiumUpgrade
            )

            SpatialHubCard(
                title = "توثيق الواجهات البرمجية",
                subtitle = "دليل الربط البرمجي للمطورين API",
                icon = Icons.Default.Code,
                badge = "API Docs",
                color = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
                onClick = onViewApiDocs
            )
        }

        // 🌐 بوابة قبس الرسمية والمستودع السحابي
        val context = LocalContext.current
        SpatialHubCard(
            title = "بوابة قبس والمستودع السحابي 🌐",
            subtitle = "الموقع الرسمي: تحميل الـ APK، متابعة التحديثات، والدروس العلمية",
            icon = Icons.Default.Language,
            badge = "Official Web Hub ✦",
            color = Color(0xFF00E5FF),
            modifier = Modifier.fillMaxWidth(),
            onClick = { QabasWebConfig.openWebsite(context) }
        )

        if (isAdmin) {
            SpatialHubCard(
                title = "لوحة تحكم المطورين والمفاتيح",
                subtitle = "تشخيص الخوادم، مفاتيح الذكاء، ومراقبة النظام",
                icon = Icons.Default.Terminal,
                badge = "Dev Matrix",
                color = Color(0xFFEF4444),
                modifier = Modifier.fillMaxWidth(),
                onClick = onDeveloperDashboard
            )
        }
    }
}

/**
 * قطاع 4: مشاريعي والمسودات المحفوظة
 */
@Composable
private fun SpatialProjectsSector(
    recentProjects: List<ProjectService.Project>,
    onOpenProject: (ProjectService.Project) -> Unit,
    onViewProjects: () -> Unit,
    onNewProject: () -> Unit
) {
    val primaryColor = qabasPrimary()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (recentProjects.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.8f)),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = primaryColor, modifier = Modifier.size(36.dp))
                    Text("لا توجد مشاريع سابقة في المصفوفة", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("ابدأ بصناعة أول فيديو دعوي الآن عبر محرك التوليد", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onNewProject,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = CutCornerShape(8.dp)
                    ) {
                        Text("إنشاء مشروع جديد ✨", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "أحدث المشاريع (${recentProjects.size})",
                    color = primaryColor,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                TextButton(onClick = onViewProjects) {
                    Text("عرض كل المشاريع", color = primaryColor, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }

            recentProjects.take(4).forEach { proj ->
                SpatialProjectCardItem(
                    project = proj,
                    onClick = { onOpenProject(proj) }
                )
            }

            Button(
                onClick = onViewProjects,
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
            ) {
                Text("إدارة وفتح كل المشاريع (${recentProjects.size}) 📂", color = primaryColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
    }
}

/**
 * بطاقة مشروع فضائية زجاجية
 */
@Composable
private fun SpatialProjectCardItem(
    project: ProjectService.Project,
    onClick: () -> Unit
) {
    val primaryColor = qabasPrimary()

    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = CutCornerShape(12.dp),
        backgroundColor = CardSurface.copy(alpha = 0.85f),
        borderColor = primaryColor.copy(alpha = 0.25f)
    ) {
        HudCornerDecor(modifier = Modifier.matchParentSize(), color = primaryColor.copy(alpha = 0.1f))
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
                        .size(40.dp)
                        .clip(CutCornerShape(6.dp))
                        .background(primaryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = project.title.ifBlank { "مشروع فيديو جديد" },
                        color = TextPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "الحالة: ${if (project.status.isNotBlank()) project.status else "مسودة"}",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.5.sp
                    )
                }
            }
            Icon(Icons.Default.PlayCircleFilled, contentDescription = "فتح", tint = primaryColor, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * بطاقة خدمة هولوغرافية تفاعلية
 */
@Composable
private fun SpatialHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CutCornerShape(12.dp),
        color = CardSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Box {
            // Tiny corner accent
            Canvas(modifier = Modifier.size(20.dp).align(Alignment.TopStart)) {
                val thickness = 1.dp.toPx()
                drawLine(color.copy(alpha = 0.6f), Offset(0f, 0f), Offset(10.dp.toPx(), 0f), thickness)
                drawLine(color.copy(alpha = 0.6f), Offset(0f, 0f), Offset(0f, 10.dp.toPx()), thickness)
            }
            
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CutCornerShape(8.dp))
                            .background(color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    }

                    Surface(
                        color = color.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, color.copy(alpha = 0.4f)),
                        shape = CutCornerShape(4.dp)
                    ) {
                        Text(
                            text = badge,
                            color = color,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * شريط المعلومات المتحرك المطور (Dynamic System Ticker)
 */
@Composable
private fun SpatialSystemTicker() {
    val context = LocalContext.current
    val isDeveloper = remember(context) { AdminGuard.isDashboardAccessAllowed(context) }
    QabasSmartTipsBar(isDeveloper = isDeveloper)
}

/**
 * شريط الحالة والقياسات الكوانتية والتحية والساعة الحية (Live HUD Clock, Greeting & City Selector)
 */
@Composable
private fun SpatialHudStatsHeader(
    currentTimeMillis: Long,
    selectedCity: CityLocation,
    onOpenCitySelector: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()
    
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val savedDisplayName = remember(prefs) {
        prefs.getString("user_display_name", null)
            ?: CloudServices.Auth.currentUser.value?.displayName
            ?: "مستخدم قبس"
    }

    val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "صباح الأنوار والفتح المبين 🌅"
        in 12..16 -> "طاب يومك بنشر الخير والأثر ☀️"
        in 17..20 -> "مساء السكينة والذكر الحكيم 🌙"
        else -> "ليلة مباركة في ظلال الوحيين ✨"
    }

    // Time & Date Formats
    val timeFormat = remember { java.text.SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
    val formattedTime = remember(currentTimeMillis) { timeFormat.format(calendar.time) }
    val dateFormat = remember { java.text.SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")) }
    val formattedDate = remember(currentTimeMillis) { dateFormat.format(calendar.time) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // بطاقة الترحيب والساعة الرقمية الحية الكوانتية (Linear Minimalist Spatial Card)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = CardSurface.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // الصف العلوي: التحية واسم المستخدم + زر المدينة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = greeting,
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "• $savedDisplayName",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    // زر المدينة
                    Surface(
                        onClick = onOpenCitySelector,
                        shape = RoundedCornerShape(12.dp),
                        color = primaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = primaryColor, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${selectedCity.nameAr} ▾",
                                color = primaryColor,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // الصف السفلي: التاريخ والساعة المباشرة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedDate,
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedTime,
                            color = primaryColor,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
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
