package com.qabas.app

/**
 * AppNavigation.kt
 * مسار الإنشاء الرئيسي:
 * INPUT → UNDERSTANDING → RESOURCES → PROCESSING → REVIEW → …
 */

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    state: ProjectState,
    viewModel: ProjectViewModel,
    context: Context,
    bottomNav: @Composable () -> Unit
) {
    val appState = state.appState

    // Reactive Auth state observer broadcasting from SupabaseRepository
    ReactiveAuthNavigationObserver(
        onAuthenticated = { isAdmin ->
            if (appState == AppState.LOGIN || appState == AppState.REGISTER || appState == AppState.FORGOT_PASSWORD) {
                val nextState = AppState.HOME
                viewModel.updateState { copy(appState = nextState) }
            }
        },
        onUnauthenticated = {
            if (appState != AppState.DATA_LOADING && appState != AppState.ONBOARDING && appState != AppState.LOGIN && appState != AppState.REGISTER && appState != AppState.FORGOT_PASSWORD) {
                viewModel.updateState { copy(appState = AppState.LOGIN) }
            }
        }
    )

    LaunchedEffect(appState) {
        PerfTracker.onFirstFrame(appState.name)
        CrashBreadcrumbs.screen(appState.name)
        BugReportManager.currentScreen = appState.name
    }

    val accountService = remember(context) { AccountService(context) }
    var isDeveloperMode by remember { 
        mutableStateOf(accountService.isDeveloperOrAdmin) 
    }

    val restrictedProductionStates = remember {
        setOf(
            AppState.STUDIO_GATEWAY, AppState.INPUT, AppState.UNDERSTANDING, AppState.RESOURCES,
            AppState.PROCESSING, AppState.REVIEW, AppState.ADVANCED_EDIT, AppState.TRANSCRIPT_EDIT,
            AppState.PRO_EDITOR, AppState.MONEY_PRINTER_TURBO, AppState.VOICE_STUDIO,
            AppState.PHOTO_STUDIO, AppState.YOUTUBE_STUDIO, AppState.SMART_DIRECTOR,
            AppState.VIDEO_STYLE_CLONER, AppState.UNIFIED_MASTER_STUDIO, AppState.DIRECTOR_CHAT,
            AppState.QASAS, AppState.CLIP_EXTRACTOR, AppState.STYLE_SELECTION, AppState.CREATE_STYLE_OBJECT,
            AppState.TASTE_PROFILE, AppState.TELEPROMPTER, AppState.HADITH_STUDIO, AppState.AI_ASSISTANT
        )
    }

    if (!isDeveloperMode && appState in restrictedProductionStates) {
        StudioMaintenanceView(
            onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
            onDeveloperAuthenticated = {
                isDeveloperMode = true
            },
            bottomBar = bottomNav
        )
        return
    }

    when (appState) {
        AppState.DATA_LOADING -> {
            DataLoadingScreen(
                onLoadProjects = { viewModel.loadProjectsSuspend() },
                onFinished = {
                    val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                    val isLoggedIn = prefs.getBoolean("is_logged_in", false)
                    val nextState = if (isLoggedIn) AppState.HOME else AppState.LOGIN
                    viewModel.updateState { copy(appState = nextState) }
                }
            )
        }
        AppState.ONBOARDING -> {
            OnboardingScreen(
                onFinish = { viewModel.updateState { copy(appState = AppState.HOME) } }
            )
        }
        AppState.LOGIN -> {
            LoginScreen(
                onLoginSuccess = { _ ->
                    viewModel.updateState { copy(appState = AppState.HOME) }
                },
                onNavigateToRegister = { viewModel.updateState { copy(appState = AppState.REGISTER) } },
                onNavigateToForgotPassword = { viewModel.updateState { copy(appState = AppState.FORGOT_PASSWORD) } },
                onNavigateToRules = { viewModel.updateState { copy(appState = AppState.RULES) } }
            )
        }
        AppState.REGISTER -> {
            RegisterScreen(
                onRegisterSuccess = {
                    viewModel.updateState { copy(appState = AppState.HOME) }
                },
                onNavigateToLogin = { viewModel.updateState { copy(appState = AppState.LOGIN) } },
                onNavigateToRules = { viewModel.updateState { copy(appState = AppState.RULES) } }
            )
        }
        AppState.FORGOT_PASSWORD -> {
            ForgotPasswordScreen(
                onNavigateToLogin = { viewModel.updateState { copy(appState = AppState.LOGIN) } }
            )
        }
        AppState.PREMIUM_UPGRADE -> {
            PremiumUpgradeScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onUpgraded = { viewModel.updateState { copy(appState = AppState.HOME) } }
            )
        }
        AppState.HOME -> {
            HomeScreen(
                recentProjects = state.projects,
                onNewProject = { initialText ->
                    viewModel.createProject(initialText ?: "")
                    val analyticsService = AppServices.getAnalyticsService(context)
                    analyticsService.logProjectCreated("new_project", "none", "0")
                },
                onViewProjects = { viewModel.updateState { copy(appState = AppState.PROJECTS) } },
                onViewSettings = { viewModel.updateState { copy(appState = AppState.SETTINGS) } },
                onViewProfile = { viewModel.updateState { copy(appState = AppState.PROFILE) } },
                onViewNotifications = { viewModel.updateState { copy(appState = AppState.NOTIFICATIONS) } },
                onOpenProject = { project -> viewModel.openProject(project) },
                onViewApiDocs = { viewModel.updateState { copy(appState = AppState.API_DOCS) } },
                onTeleprompter = { viewModel.updateState { copy(appState = AppState.TELEPROMPTER) } },
                onAudioLibrary = { viewModel.updateState { copy(appState = AppState.AUDIO_LIBRARY) } },
                onAiAssistant = { viewModel.updateState { copy(appState = AppState.AI_ASSISTANT) } },
                onYouTubeStudio = { viewModel.updateState { copy(appState = AppState.YOUTUBE_STUDIO) } },
                onDeveloperDashboard = { viewModel.updateState { copy(appState = AppState.DEVELOPER_DASHBOARD) } },
                onLeaderboard = { viewModel.updateState { copy(appState = AppState.LEADERBOARD) } },
                onNavigateToTasteProfile = { viewModel.updateState { copy(appState = AppState.TASTE_PROFILE) } },
                onStudioGateway = { viewModel.updateState { copy(appState = AppState.STUDIO_GATEWAY) } },
                onPremiumUpgrade = { viewModel.updateState { copy(appState = AppState.PREMIUM_UPGRADE) } },
                onRewards = { viewModel.updateState { copy(appState = AppState.REWARDS) } },
                onKnowledgeHub = { viewModel.updateState { copy(appState = AppState.KNOWLEDGE_HUB) } },
                onContentGuard = { viewModel.updateState { copy(appState = AppState.CONTENT_GUARD) } },
                onQuranHub = { viewModel.updateState { copy(appState = AppState.HASANAT_HUB) } },
                onHadithStudio = { viewModel.updateState { copy(appState = AppState.HADITH_STUDIO) } },
                onVideoStyleCloner = { viewModel.updateState { copy(appState = AppState.VIDEO_STYLE_CLONER) } },
                onPhotoStudio = { viewModel.updateState { copy(appState = AppState.PHOTO_STUDIO) } },
                onStyleSelection = { viewModel.updateState { copy(appState = AppState.STYLE_SELECTION) } },
                onIslamicLibrary = { viewModel.updateState { copy(appState = AppState.ISLAMIC_LIBRARY) } },
                onAzkar = { viewModel.updateState { copy(appState = AppState.AZKAR) } },
                onPrayerTimes = { viewModel.updateState { copy(appState = AppState.PRAYER_TIMES) } },
                onScholarBiographies = { viewModel.updateState { copy(appState = AppState.SCHOLAR_BIOGRAPHIES) } },
                onProEditor = { viewModel.updateState { copy(appState = AppState.PRO_EDITOR) } },
                onMoneyPrinterTurbo = { viewModel.updateState { copy(appState = AppState.MONEY_PRINTER_TURBO) } },
                onVoiceStudio = { viewModel.updateState { copy(appState = AppState.VOICE_STUDIO) } },
                onMediaBrowser = { viewModel.updateState { copy(appState = AppState.MEDIA_BROWSER) } },
                onReels = { viewModel.updateState { copy(appState = AppState.REELS) } },
                onRestoreWorkspaceDraft = { viewModel.restoreWorkspaceDraft() },
                onRules = { viewModel.updateState { copy(appState = AppState.RULES) } },
                onAiStudioAgent = { viewModel.updateState { copy(appState = AppState.AI_STUDIO_AGENT) } },
                onViewDownloads = { viewModel.updateState { copy(appState = AppState.DOWNLOAD_MANAGER) } },
                bottomBar = bottomNav
            )
        }
        AppState.PHOTO_STUDIO -> {
            PhotoStudioScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onNavigateToAdvancedEdit = { videoPath ->
                    viewModel.updateState {
                        copy(
                            libraryScenes = listOf(Scene("فيديو استوديو الصور", "فيديو جاهز", 15, mediaUrl = videoPath)),
                            appState = AppState.ADVANCED_EDIT
                        )
                    }
                    // Persist PhotoStudio export to Supabase so it shows up
                    // in cloud history alongside the local Room copy.
                    viewModel.viewModelScope.launch {
                        SupabaseServices.Database.saveProject(
                            title = "استوديو الصور - فيديو سريع",
                            description = "تم توليد الفيديو من استوديو الصور",
                            status = "ready",
                            cost = 0.0,
                            tags = listOf("photo_studio", "generated"),
                            metadata = mapOf(
                                "sourceType" to "photo_studio",
                                "videoPath" to videoPath
                            )
                        )
                    }
                },
                onSaveProject = { videoPath ->
                    viewModel.createProject("استوديو الصور - فيديو سريع")
                    viewModel.updateState {
                        copy(
                            appState = AppState.PROJECTS,
                            finalVideoPath = videoPath
                        )
                    }
                    // Persist PhotoStudio export to Supabase cloud history.
                    viewModel.viewModelScope.launch {
                        SupabaseServices.Database.saveProject(
                            title = "استوديو الصور - فيديو سريع",
                            description = "تم توليد الفيديو من استوديو الصور",
                            status = "ready",
                            cost = 0.0,
                            tags = listOf("photo_studio", "saved"),
                            metadata = mapOf(
                                "sourceType" to "photo_studio",
                                "videoPath" to videoPath
                            )
                        )
                    }
                }
            )
        }
        AppState.DEVELOPER_DASHBOARD -> {
            DeveloperDashboardScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onOpenChat = { reqId ->
                    viewModel.updateState { copy(appState = AppState.REQUEST_CHAT, selectedRequestId = reqId) }
                },
                onNavigateTo = { viewModel.updateState { copy(appState = it) } }
            )
        }
        AppState.QURAN_HUB -> {
            QuranTajweedScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onCreateVideoFromVerse = { verseText, surahName, verseNumber ->
                    viewModel.updateState {
                        copy(
                            selectedVerseText = verseText,
                            selectedSurahName = surahName,
                            selectedVerseNumber = verseNumber,
                            appState = AppState.HADITH_STUDIO,
                            selectedRatio = "9:16"
                        )
                    }
                },
                onNavigateToAzkar = { viewModel.updateState { copy(appState = AppState.AZKAR) } },
                bottomBar = bottomNav
            )
        }
        AppState.HASANAT_HUB -> {
            HasanatHubScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onNavigate = { newState -> viewModel.updateState { copy(appState = newState) } },
                bottomBar = bottomNav
            )
        }
        AppState.AZKAR -> {
            AzkarScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HASANAT_HUB) } }
            )
        }
        AppState.PRAYER_TIMES -> {
            PrayerTimesScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HASANAT_HUB) } },
                bottomBar = bottomNav
            )
        }
        AppState.SCHOLAR_BIOGRAPHIES -> {
            ScholarBiographiesScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HASANAT_HUB) } },
                onScholarClick = { scholar ->
                    viewModel.updateState { copy(selectedScholar = scholar, appState = AppState.SCHOLAR_DETAIL) }
                }
            )
        }
        AppState.SCHOLAR_DETAIL -> {
            state.selectedScholar?.let { scholar ->
                ScholarDetailScreen(
                    scholar = scholar,
                    onBack = { viewModel.updateState { copy(appState = AppState.SCHOLAR_BIOGRAPHIES) } }
                )
            } ?: run {
                viewModel.updateState { copy(appState = AppState.SCHOLAR_BIOGRAPHIES) }
            }
        }
        AppState.STUDIO_GATEWAY -> {
            StudioGatewayScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onNavigateToMasterStudio = { viewModel.updateState { copy(appState = AppState.UNIFIED_MASTER_STUDIO) } },
                onNavigateToMediaBrowser = { viewModel.updateState { copy(appState = AppState.MEDIA_BROWSER) } },
                onNavigateToAiStudio = { viewModel.updateState { copy(appState = AppState.INPUT) } },
                onNavigateToProEditor = { viewModel.updateState { copy(appState = AppState.PRO_EDITOR) } },
                onNavigateToMoneyPrinter = { viewModel.updateState { copy(appState = AppState.MONEY_PRINTER_TURBO) } },
                onNavigateToPhotoStudio = { viewModel.updateState { copy(appState = AppState.PHOTO_STUDIO) } },
                onNavigateToVoiceStudio = { viewModel.updateState { copy(appState = AppState.VOICE_STUDIO) } },
                onNavigateToYouTubeStudio = { viewModel.updateState { copy(appState = AppState.YOUTUBE_STUDIO) } },
                onNavigateToTeleprompter = { viewModel.updateState { copy(appState = AppState.TELEPROMPTER) } },
                onNavigateToStyleCloner = { viewModel.updateState { copy(appState = AppState.VIDEO_STYLE_CLONER) } },
                onNavigateToHadithStudio = { viewModel.updateState { copy(appState = AppState.HADITH_STUDIO) } },
                onNavigateToStats = { viewModel.updateState { copy(appState = AppState.SOCIAL_STATS) } },
                onNavigateToAiStudioAgent = { viewModel.updateState { copy(appState = AppState.AI_STUDIO_AGENT) } },
                bottomBar = bottomNav
            )
        }
        AppState.PRO_EDITOR -> {
            StudioProEditorScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.STUDIO_GATEWAY) } },
                onSave = {
                    Toast.makeText(context, Translator.tr("جاري معالجة وتصدير الفيديو..."), Toast.LENGTH_LONG).show()
                    viewModel.updateState { copy(appState = AppState.PROJECTS) }
                }
            )
        }
        AppState.SOCIAL_STATS -> {
            SocialStatsScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.STUDIO_GATEWAY) } },
                bottomBar = bottomNav
            )
        }
        AppState.YOUTUBE_STUDIO -> {
            YouTubeStudioScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onStartSeries = {
                    viewModel.updateState { copy(appState = AppState.PROJECTS) }
                },
                onStartEpisode = { title, scriptText, format ->
                    val selectedRatio = if (format.contains("9:16")) "9:16" else if (format.contains("16:9")) "16:9" else "4:5"
                    viewModel.createProject(title)
                    viewModel.updateState {
                        copy(
                            appState = AppState.RESOURCES,
                            inputText = scriptText,
                            selectedRatio = selectedRatio,
                            contentType = if (selectedRatio == "9:16") "ريلز / شورتس" else "فيديو يوتيوب",
                            videoDuration = "60 ثانية"
                        )
                    }
                }
            )
        }
        AppState.API_DOCS -> {
            ApiDocsScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } }
            )
        }
        AppState.PROJECTS -> {
            ProjectsScreen(
                projects = state.projects,
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onOpenProject = { project -> viewModel.openProject(project) },
                onDeleteProject = { id -> viewModel.deleteProject(id) },
                onViewSettings = { viewModel.updateState { copy(appState = AppState.SETTINGS) } },
                bottomBar = bottomNav
            )
        }
        AppState.HADITH_STUDIO -> {
            QabasStudioScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                initialVerseText = state.selectedVerseText,
                initialSurahName = state.selectedSurahName,
                initialVerseNumber = state.selectedVerseNumber,
                onExportAsVideo = { text, ratio ->
                    viewModel.createProject(text)
                    viewModel.updateState {
                        copy(
                            appState = AppState.INPUT,
                            selectedRatio = ratio,
                            contentType = if (state.selectedVerseText != null) "آية قرآنية" else "بطاقة حديث شريف",
                            videoDuration = "15 ثانية"
                        )
                    }
                },
                onViewProjects = { viewModel.updateState { copy(appState = AppState.PROJECTS) } },
                bottomBar = bottomNav
            )
        }
        AppState.ENTERPRISE_PORTAL -> {
            EnterprisePortalScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onNavigate = { newState -> viewModel.updateState { copy(appState = newState) } },
                bottomBar = bottomNav
            )
        }
        AppState.SETTINGS -> {
            SettingsScreen(
                onLogout = {
                    viewModel.viewModelScope.launch {
                        runCatching { SupabaseServices.Auth.logout() }
                    }
                    context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE).edit()
                        .remove("is_logged_in")
                        .remove("is_admin")
                        .remove("is_developer")
                        .remove("user_email")
                        .remove("user_name")
                        .apply()
                    viewModel.updateState { copy(appState = AppState.LOGIN) }
                },
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onNavigateToTasteProfile = { viewModel.updateState { copy(appState = AppState.TASTE_PROFILE) } },
                onNavigateToPremiumUpgrade = { viewModel.updateState { copy(appState = AppState.PREMIUM_UPGRADE) } },
                onNavigateToAboutUs = { viewModel.updateState { copy(appState = AppState.ABOUT_US) } },
                onNavigateToRules = { viewModel.updateState { copy(appState = AppState.RULES) } },
                bottomBar = bottomNav
            )
        }
        AppState.ABOUT_US -> {
            AboutUsAndVersionScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.SETTINGS) } },
                bottomBar = bottomNav
            )
        }
        AppState.RULES -> {
            val isLoggedIn = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE).getBoolean("is_logged_in", false)
            QabasRulesScreen(
                onBack = { viewModel.updateState { copy(appState = if (isLoggedIn) AppState.SETTINGS else AppState.LOGIN) } }
            )
        }
        AppState.AI_STUDIO_AGENT -> {
            AiStudioAgentScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onOpenDeveloperDashboard = { viewModel.updateState { copy(appState = AppState.DEVELOPER_DASHBOARD) } },
                onOpenTeamConfig = { viewModel.updateState { copy(appState = AppState.AGENT_TEAM_CONFIG) } },
                bottomBar = bottomNav
            )
        }
        AppState.AGENT_TEAM_CONFIG -> {
            AgentTeamConfigScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.AI_STUDIO_AGENT) } },
                onSave = { team ->
                    viewModel.updateState { copy(appState = AppState.AI_STUDIO_AGENT) }
                },
                onOpenKeyHub = {
                    viewModel.updateState { copy(appState = AppState.DEVELOPER_KEY_STATUS_HUB) }
                }
            )
        }
        AppState.TELEPROMPTER -> {
            TeleprompterScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                initialScript = state.inputText.takeIf { it.isNotBlank() },
                onNavigateToEditor = { script, audioPath ->
                    viewModel.createProject(script)
                    if (!audioPath.isNullOrBlank()) {
                        viewModel.updateState { copy(voiceOver = audioPath) }
                    }
                    viewModel.updateState {
                        copy(
                            appState = AppState.ADVANCED_EDIT,
                            selectedRatio = "9:16",
                            contentType = "تسجيل مُلقن ذكي",
                            videoDuration = "30 ثانية"
                        )
                    }
                }
            )
        }
        AppState.AUDIO_LIBRARY -> {
            AudioLibraryScreen(
                onBack = {
                    viewModel.updateState {
                        copy(appState = if (inputText.isNotBlank()) AppState.INPUT else AppState.HOME)
                    }
                },
                onAudioSelected = { audioUrl ->
                    viewModel.updateState {
                        copy(
                            voiceOver = audioUrl,
                            appState = if (inputText.isNotBlank()) AppState.INPUT else AppState.HOME
                        )
                    }
                },
                onApplyToProject = { audioPath, usageType ->
                    viewModel.updateState {
                        if (usageType == "ambient") {
                            copy(
                                musicVibe = audioPath,
                                ambientSound = audioPath,
                                appState = if (inputText.isNotBlank()) AppState.INPUT else AppState.HOME
                            )
                        } else {
                            copy(
                                voiceOver = audioPath,
                                appState = if (inputText.isNotBlank()) AppState.INPUT else AppState.HOME
                            )
                        }
                    }
                }
            )
        }
        AppState.AI_ASSISTANT -> {
            AIAssistantScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onCreateProjectFromScript = { scriptText ->
                    viewModel.createProject(scriptText)
                    viewModel.updateState {
                        copy(
                            appState = AppState.INPUT,
                            selectedRatio = "9:16",
                            contentType = "ريلز / شورتس",
                            videoDuration = "30 ثانية"
                        )
                    }
                }
            )
        }
        AppState.LEADERBOARD -> {
            LeaderboardScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onUseInspirationScript = { script, topic ->
                    viewModel.createProject(script)
                    viewModel.updateState {
                        copy(
                            projectTitle = topic.ifBlank { "مشروع إلهام - قبس" },
                            inputText = script,
                            selectedRatio = "9:16",
                            contentType = "ريلز / شورتس",
                            videoDuration = "30 ثانية",
                            appState = AppState.INPUT
                        )
                    }
                },
                onOpenRewards = {
                    viewModel.updateState { copy(appState = AppState.REWARDS) }
                }
            )
        }
        AppState.NOTIFICATIONS -> {
            NotificationsScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onOpenContentGuard = { viewModel.updateState { copy(appState = AppState.CONTENT_GUARD) } }
            )
        }
        AppState.PROFILE -> {
            ProfileScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onNavigate = { newState -> viewModel.updateState { copy(appState = newState) } }
            )
        }
        AppState.SAVED_ITEMS -> {
            val savedItemsViewModel = remember(context) {
                val db = FirebaseManager.getFirestore(context)
                val repo = SavedItemsRepository(db)
                val authRepo = FirebaseAuthRepository.create(context)
                SavedItemsViewModel(repo, authRepo)
            }
            SavedItemsScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.PROFILE) } },
                viewModel = savedItemsViewModel
            )
        }
        AppState.REELS -> {
            ReelsScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                bottomBar = bottomNav
            )
        }
        AppState.DOWNLOAD_MANAGER -> {
            DownloadsHubScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onOpenReels = { viewModel.updateState { copy(appState = AppState.REELS) } },
                onOpenAiAssistant = { viewModel.updateState { copy(appState = AppState.AI_ASSISTANT) } },
                bottomBar = bottomNav
            )
        }
        AppState.INPUT -> {
            InputScreen(
                state = state,
                onStateChange = { newState -> viewModel.updateState { newState } },
                onProceed = { viewModel.updateState { copy(appState = AppState.UNDERSTANDING) } },
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onOpenAudioLibrary = { viewModel.updateState { copy(appState = AppState.AUDIO_LIBRARY) } },
                onOpenDirector = { viewModel.updateState { copy(appState = AppState.DIRECTOR_CHAT) } },
                onOpenQasas = { viewModel.updateState { copy(appState = AppState.QASAS) } },
                onOpenClips = { viewModel.updateState { copy(appState = AppState.CLIP_EXTRACTOR) } }
            )
        }
        AppState.DIRECTOR_CHAT -> {
            DirectorChatScreen(
                initialText = state.inputText,
                onBack = { viewModel.updateState { copy(appState = AppState.INPUT) } },
                onComplete = { brief ->
                    viewModel.createProject(brief.idea)
                    viewModel.updateState {
                        copy(
                            inputText = brief.idea,
                            contentType = brief.contentType.ifBlank { "ريلز / شورتس" },
                            contentTone = brief.contentTone.ifBlank { "خاشع" },
                            videoDuration = brief.videoDuration.ifBlank { "30 ثانية" },
                            selectedRatio = brief.selectedRatio.ifBlank { "9:16" },
                            appState = AppState.UNDERSTANDING
                        )
                    }
                }
            )
        }
        AppState.UNDERSTANDING -> {
            UnderstandingScreen(
                inputText = state.inputText,
                contentType = state.contentType,
                contentTone = state.contentTone,
                selectedRatio = state.selectedRatio,
                videoDuration = state.videoDuration,
                videoStyleAnalysis = state.videoStyleAnalysis,
                styleDescription = state.styleDescription,
                onStyleDescriptionChange = { newStyle ->
                    viewModel.updateState { copy(styleDescription = newStyle) }
                },
                onEditIdea = { viewModel.updateState { copy(appState = AppState.INPUT) } },
                onProceed = {
                    val cur = state.styleDescription
                    val withCaptions = if (cur.contains("kinetic captions")) cur else (cur + CaptionStyles.VIRAL_DIRECTIVE)
                    viewModel.updateState { copy(styleDescription = withCaptions, appState = AppState.RESOURCES) }
                }
            )
        }
        AppState.PROCESSING -> {
            val scenesForEngine = state.scenesToProcess?.takeIf { it.isNotEmpty() }
            ProcessingScreen(
                inputText = state.inputText,
                styleDescription = state.styleDescription,
                selectedTemplate = state.selectedTemplate.ifEmpty { "الافتراضي" },
                videoQuality = state.videoQuality.ifEmpty { "1080p" },
                ambientSound = state.ambientSound.ifEmpty { "بدون" },
                videoStyleAnalysis = state.videoStyleAnalysis,
                contentType = state.contentType,
                contentTone = state.contentTone,
                videoDuration = state.videoDuration,
                existingScenes = scenesForEngine,
                onProcessingComplete = { processedScenes ->
                    try { StreakManager.recordProduction(context) } catch (_: Exception) {}
                    viewModel.updateState {
                        copy(
                            libraryScenes = processedScenes,
                            scenesToProcess = processedScenes,
                            finalVideoPath = processedScenes.firstOrNull()?.mediaUrl ?: "",
                            appState = AppState.REVIEW
                        )
                    }
                },
                onCancel = {
                    val backState = if (scenesForEngine != null) AppState.REVIEW else AppState.RESOURCES
                    viewModel.updateState { copy(appState = backState) }
                }
            )
        }
        AppState.REVIEW -> {
            ReviewScreen(
                state = state,
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onUpdateScenes = { updatedScenes ->
                    viewModel.updateState { copy(libraryScenes = updatedScenes, scenesToProcess = updatedScenes) }
                },
                onUpdateAmbientSound = { sound ->
                    viewModel.updateState { copy(ambientSound = sound) }
                },
                onAdvancedEdit = { viewModel.updateState { copy(appState = AppState.TRANSCRIPT_EDIT) } },
                onApprove = { viewModel.updateState { copy(appState = AppState.SAVE_SHARE) } }
            )
        }
        AppState.TRANSCRIPT_EDIT -> {
            val scenesToEdit = (state.scenesToProcess?.takeIf { it.isNotEmpty() } ?: state.libraryScenes).ifEmpty {
                listOf(Scene("المقدمة", "مشهد افتتاحي", 3))
            }
            TranscriptVideoEditorScreen(
                scenes = scenesToEdit,
                onBack = { viewModel.updateState { copy(appState = AppState.REVIEW) } },
                onApply = { updatedScenes ->
                    viewModel.updateState { copy(libraryScenes = updatedScenes, scenesToProcess = updatedScenes, appState = AppState.REVIEW) }
                }
            )
        }
        AppState.QASAS -> {
            QasasStorytellerScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onCreateProject = { script, topic ->
                    viewModel.createProject(script)
                    viewModel.updateState {
                        copy(projectTitle = topic.ifBlank { "قصة - قبس" }, inputText = script,
                            selectedRatio = "9:16", contentType = "قصة", videoDuration = "30 ثانية",
                            appState = AppState.UNDERSTANDING)
                    }
                }
            )
        }
        AppState.CLIP_EXTRACTOR -> {
            ClipExtractorScreen(
                initialText = state.inputText,
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onCreateProject = { script, topic ->
                    viewModel.createProject(script)
                    viewModel.updateState {
                        copy(projectTitle = topic.ifBlank { "مقطع - قبس" }, inputText = script,
                            selectedRatio = "9:16", contentType = "موعظة", videoDuration = "30 ثانية",
                            appState = AppState.UNDERSTANDING)
                    }
                }
            )
        }
        AppState.ADVANCED_EDIT -> {
            val scenesToEdit = (state.scenesToProcess?.takeIf { it.isNotEmpty() } ?: state.libraryScenes).ifEmpty {
                listOf(
                    Scene("المقدمة", "مشهد افتتاحي مهيب مع شروق الشمس", 3),
                    Scene("المشهد الرئيسي", "عرض النص بأسلوب حركي تفاعلي", 5),
                    Scene("الخاتمة", "خاتمة هادئة مع شعار التطبيق", 4)
                )
            }
            AdvancedEditScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.REVIEW) } },
                scenes = scenesToEdit,
                onUpdateScenes = { updatedScenes ->
                    viewModel.updateState {
                        copy(
                            libraryScenes = updatedScenes,
                            scenesToProcess = updatedScenes,
                            appState = AppState.REVIEW
                        )
                    }
                }
            )
        }
        AppState.PAYMENT -> {
            PaymentScreen(
                onPaymentSuccess = { viewModel.updateState { copy(appState = AppState.SAVE_SHARE) } },
                onBack = { viewModel.updateState { copy(appState = AppState.REVIEW) } }
            )
        }
        AppState.SAVE_SHARE -> {
            if (state.libraryScenes.isEmpty() && state.finalVideoPath.isBlank()) {
                ReviewScreen(
                    state = state,
                    onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                    onUpdateScenes = { updatedScenes ->
                        viewModel.updateState { copy(libraryScenes = updatedScenes, scenesToProcess = updatedScenes) }
                    },
                    onUpdateAmbientSound = { sound ->
                        viewModel.updateState { copy(ambientSound = sound) }
                    },
                    onAdvancedEdit = { viewModel.updateState { copy(appState = AppState.ADVANCED_EDIT) } },
                    onApprove = { viewModel.updateState { copy(appState = AppState.SAVE_SHARE) } }
                )
            } else {
            SaveShareScreen(
                scriptText = state.inputText,
                videoDuration = state.videoDuration,
                selectedRatio = state.selectedRatio,
                scenes = state.libraryScenes,
                ambientSound = state.ambientSound,
                videoStyleAnalysis = state.videoStyleAnalysis,
                onBackToHome = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onBackToEditor = { viewModel.updateState { copy(appState = AppState.ADVANCED_EDIT) } },
                onStartSeries = {
                    viewModel.updateState {
                        copy(
                            appState = AppState.INPUT,
                            inputText = "",
                            mediaResources = emptyList(),
                            libraryScenes = emptyList()
                        )
                    }
                }
            )
            }
        }
        AppState.SMART_DIRECTOR -> {
            AiSmartDirectorScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.STUDIO_GATEWAY) } },
                onMakeMagic = { viewModel.updateState { copy(appState = AppState.ADVANCED_EDIT) } }
            )
        }
        AppState.REWARDS -> {
            RewardsScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } }
            )
        }
        AppState.KNOWLEDGE_HUB -> {
            KnowledgeHubScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onStartScriptWithText = { script ->
                    viewModel.updateState {
                        copy(
                            appState = AppState.INPUT,
                            inputText = script,
                            selectedRatio = "9:16",
                            contentType = "قصة وتزكية"
                        )
                    }
                },
                onOpenTeleprompterWithText = { _ ->
                    viewModel.updateState { copy(appState = AppState.TELEPROMPTER) }
                }
            )
        }
        AppState.CONTENT_GUARD -> {
            ContentGuardScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onStartScriptWithText = { script ->
                    viewModel.updateState {
                        copy(
                            appState = AppState.INPUT,
                            inputText = script,
                            selectedRatio = "9:16",
                            contentType = "مدافعة ورد الشبهات"
                        )
                    }
                },
                onOpenTeleprompterWithText = { _ ->
                    viewModel.updateState { copy(appState = AppState.TELEPROMPTER) }
                }
            )
        }
        AppState.VIDEO_STYLE_CLONER -> {
            VideoStyleClonerScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onGenerateProjectWithStyle = { topic, style ->
                    val richStyleDescription = buildString {
                        append("الأسلوب المستنسخ: ${style.detectedStyle}. ")
                        append("لوحة الألوان والتباين: ${style.dominantColors}. ")
                        append("سرعة تقطيع المشاهد: ${style.transitionSpeed}. ")
                        append("حركة الكاميرا والزووم: ${style.movementPatterns}. ")
                        append("الهوية الصوتية: ${style.audioStyle}. ")
                        append("نمط الكابشن والخط: ${style.typographyStyle}. ")
                        append("نبرة المحتوى: ${style.contentTone}. ")
                        if (style.keywords.isNotEmpty()) {
                            append("كلمات مفتاحية: ${style.keywords.joinToString(", ")}. ")
                        }
                    }
                    viewModel.createProject(topic)
                    viewModel.updateState {
                        copy(
                            videoStyleAnalysis = style,
                            styleDescription = richStyleDescription,
                            inputText = topic,
                            contentTone = style.contentTone.ifBlank { "مؤثر" },
                            appState = AppState.RESOURCES
                        )
                    }
                }
            )
        }
        AppState.TASTE_PROFILE -> {
            TasteProfileScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } }
            )
        }
        AppState.STYLE_SELECTION -> {
            StyleSelectionScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onApplyMasterStyleToProject = { masterStyle ->
                    val analysis = masterStyle.toVideoStyleAnalysis()
                    viewModel.createProject(masterStyle.name)
                    viewModel.updateState {
                        copy(
                            videoStyleAnalysis = analysis,
                            styleDescription = masterStyle.fusionSummary,
                            editingStyle = masterStyle.name,
                            contentTone = masterStyle.contentTone.tone,
                            appState = AppState.INPUT
                        )
                    }
                },
                onCreateCustomStyle = {
                    viewModel.updateState { copy(appState = AppState.CREATE_STYLE_OBJECT) }
                }
            )
        }
        AppState.CREATE_STYLE_OBJECT -> {
            CreateStyleObjectScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.STYLE_SELECTION) } },
                onStyleSaved = { _ ->
                    viewModel.updateState { copy(appState = AppState.STYLE_SELECTION) }
                },
                onApplyDirectlyToProject = { styleObject ->
                    val analysis = styleObject.toVideoStyleAnalysis()
                    viewModel.createProject(styleObject.name)
                    viewModel.updateState {
                        copy(
                            videoStyleAnalysis = analysis,
                            styleDescription = styleObject.analysisSummary,
                            editingStyle = styleObject.name,
                            contentTone = styleObject.contentTone.tone,
                            appState = AppState.INPUT
                        )
                    }
                }
            )
        }
        AppState.RESOURCES -> {
            ResourceReviewScreen(
                ideaText = state.inputText,
                mediaResources = state.mediaResources,
                onAddResource = { resource ->
                    viewModel.updateState { copy(mediaResources = mediaResources + resource) }
                },
                onUpdateResource = { updatedResource ->
                    viewModel.updateState {
                        copy(mediaResources = mediaResources.map { if (it.id == updatedResource.id) updatedResource else it })
                    }
                },
                onRemoveResource = { resId ->
                    viewModel.updateState { copy(mediaResources = mediaResources.filter { it.id != resId }) }
                },
                onNext = { viewModel.updateState { copy(appState = AppState.PROCESSING) } },
                onBack = { viewModel.updateState { copy(appState = AppState.UNDERSTANDING) } },
                selectedRatio = state.selectedRatio,
                onRatioChange = { ratio -> viewModel.updateState { copy(selectedRatio = ratio) } },
                videoDuration = state.videoDuration,
                onDurationChange = { dur -> viewModel.updateState { copy(videoDuration = dur) } },
                editingStyle = state.editingStyle,
                onStyleChange = { st -> viewModel.updateState { copy(editingStyle = st) } },
                voiceOver = state.voiceOver,
                onVoiceChange = { v -> viewModel.updateState { copy(voiceOver = v) } },
                ambientSound = state.ambientSound,
                onAmbientChange = { amb -> viewModel.updateState { copy(ambientSound = amb) } },
                videoQuality = state.videoQuality,
                onQualityChange = { q -> viewModel.updateState { copy(videoQuality = q) } },
                videoStyleAnalysis = state.videoStyleAnalysis
            )
        }
        AppState.APP_IDEA_FORM -> {
            AppIdeaFormScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onSubmitSuccess = { viewModel.updateState { copy(appState = AppState.HOME) } }
            )
        }
        AppState.REQUEST_CHAT -> {
            RequestChatScreen(
                requestId = state.selectedRequestId ?: "",
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onOpenDashboardSection = { section ->
                    viewModel.updateState { copy(appState = AppState.DEVELOPER_DASHBOARD) }
                },
                onOpenDetails = { viewModel.updateState { copy(appState = AppState.REQUEST_DETAILS) } }
            )
        }
        AppState.REQUEST_DETAILS -> {
            RequestDetailsScreen(
                requestId = state.selectedRequestId ?: "",
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onOpenChat = { viewModel.updateState { copy(appState = AppState.REQUEST_CHAT) } }
            )
        }
        AppState.SAVE_PROJECT -> {
            SaveShareScreen(
                scriptText = state.inputText,
                videoDuration = state.videoDuration,
                selectedRatio = state.selectedRatio,
                scenes = state.libraryScenes,
                ambientSound = state.ambientSound,
                videoStyleAnalysis = state.videoStyleAnalysis,
                onBackToHome = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onBackToEditor = { viewModel.updateState { copy(appState = AppState.ADVANCED_EDIT) } },
                onStartSeries = {
                    viewModel.updateState {
                        copy(
                            appState = AppState.INPUT,
                            inputText = "",
                            mediaResources = emptyList(),
                            libraryScenes = emptyList()
                        )
                    }
                }
            )
        }
        AppState.CAMERA_CAPTURE -> {
            CameraCaptureScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onVideoCaptured = { videoPath ->
                    viewModel.updateState {
                        copy(
                            appState = AppState.ADVANCED_EDIT,
                            libraryScenes = listOf(Scene("فيديو مسجل 🎥", "تسجيل الكاميرا المباشر", 15, mediaUrl = videoPath))
                        )
                    }
                },
                onPhotoCaptured = { photoPath ->
                    viewModel.updateState { copy(appState = AppState.HOME) }
                }
            )
        }
        AppState.DEVELOPER_KEY_STATUS_HUB -> {
            DeveloperKeyStatusHub(
                onBack = { viewModel.updateState { copy(appState = AppState.DEVELOPER_DASHBOARD) } },
                onNavigateToEditKey = { serviceKey ->
                    viewModel.updateState { copy(appState = AppState.SETTINGS) }
                },
                showTopBar = true
            )
        }
        AppState.ISLAMIC_LIBRARY -> {
            IslamicLibraryScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HASANAT_HUB) } },
                onNavigateToVideoCreation = { quote ->
                    viewModel.createProject(quote)
                    viewModel.updateState {
                        copy(
                            appState = AppState.INPUT,
                            inputText = quote
                        )
                    }
                }
            )
        }
        AppState.MONEY_PRINTER_TURBO -> {
            MoneyPrinterTurboScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onOpenInProEditor = { videoPath ->
                    viewModel.updateState {
                        copy(
                            libraryScenes = listOf(Scene("فيديو MoneyPrinter Turbo", "ريلز ذاتي جاهز", 15, mediaUrl = videoPath)),
                            appState = AppState.ADVANCED_EDIT
                        )
                    }
                },
                onOpenInYouTubeStudio = { videoPath ->
                    viewModel.updateState {
                        copy(
                            finalVideoPath = videoPath,
                            appState = AppState.YOUTUBE_STUDIO
                        )
                    }
                },
                onNavigateToVoiceCloning = {
                    viewModel.updateState { copy(appState = AppState.VOICE_STUDIO) }
                },
                onSaveProject = { title, path ->
                    viewModel.createProject(title)
                }
            )
        }
        AppState.VOICE_STUDIO -> {
            VoiceStudioScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.HOME) } },
                onUseAudioInProject = { audioPath ->
                    viewModel.createProject("مشروع صوتي - VoiceStudio")
                    viewModel.updateState {
                        copy(
                            voiceOver = audioPath,
                            appState = AppState.INPUT
                        )
                    }
                },
                onOpenInProEditor = { videoPath ->
                    viewModel.updateState {
                        copy(
                            libraryScenes = listOf(Scene("فيديو مدبلج", "فيديو VoiceStudio", 15, mediaUrl = videoPath)),
                            appState = AppState.ADVANCED_EDIT
                        )
                    }
                },
                onNavigateToMoneyPrinterWithVoice = { voiceId ->
                    viewModel.updateState {
                        copy(
                            voiceOver = "cloned:$voiceId",
                            appState = AppState.MONEY_PRINTER_TURBO
                        )
                    }
                }
            )
        }
        AppState.UNIFIED_MASTER_STUDIO -> {
            UnifiedMasterStudioScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.STUDIO_GATEWAY) } },
                initialTopic = state.inputText,
                onOpenInProEditor = { scenes, topic ->
                    viewModel.createProject(topic)
                    viewModel.updateState {
                        copy(
                            inputText = topic,
                            libraryScenes = scenes,
                            scenesToProcess = scenes,
                            appState = AppState.ADVANCED_EDIT
                        )
                    }
                },
                onExportVideo = { scenes, topic ->
                    viewModel.createProject(topic)
                    viewModel.updateState {
                        copy(
                            inputText = topic,
                            libraryScenes = scenes,
                            scenesToProcess = scenes,
                            appState = AppState.SAVE_SHARE
                        )
                    }
                }
            )
        }
        AppState.PERMISSIONS_HUB -> {
            AppPermissionsHubScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.SETTINGS) } }
            )
        }
        AppState.CRASH_LOGS_HUB -> {
            CrashLogsHubScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.SETTINGS) } }
            )
        }
        AppState.MEDIA_BROWSER -> {
            CentralMediaBrowserScreen(
                onBack = { viewModel.updateState { copy(appState = AppState.STUDIO_GATEWAY) } },
                onSelectAssetForProject = { asset ->
                    if (asset.type == CentralMediaType.AUDIO) {
                        viewModel.updateState { copy(voiceOver = asset.displayUrl, appState = AppState.INPUT) }
                    } else if (asset.type == CentralMediaType.VIDEO) {
                        viewModel.updateState {
                            copy(
                                libraryScenes = listOf(Scene(asset.title, "فيديو من مستودع الوسائط", 15, mediaUrl = asset.displayUrl)),
                                appState = AppState.ADVANCED_EDIT
                            )
                        }
                    } else {
                        viewModel.updateState { copy(appState = AppState.PHOTO_STUDIO) }
                    }
                },
                onOpenInPhotoStudio = { path ->
                    viewModel.updateState { copy(appState = AppState.PHOTO_STUDIO) }
                },
                onOpenInVideoEditor = { path ->
                    viewModel.updateState {
                        copy(
                            libraryScenes = listOf(Scene("مقطع وسائط", "من مستودع الوسائط", 15, mediaUrl = path)),
                            appState = AppState.ADVANCED_EDIT
                        )
                    }
                },
                bottomBar = bottomNav
            )
        }
        else -> {
            HomeScreen(
                recentProjects = state.projects,
                onNewProject = { initialText ->
                    viewModel.createProject(initialText ?: "")
                },
                onViewProjects = { viewModel.updateState { copy(appState = AppState.PROJECTS) } },
                onViewSettings = { viewModel.updateState { copy(appState = AppState.SETTINGS) } },
                onViewProfile = { viewModel.updateState { copy(appState = AppState.PROFILE) } },
                onViewNotifications = { viewModel.updateState { copy(appState = AppState.NOTIFICATIONS) } },
                onOpenProject = { project -> viewModel.openProject(project) },
                onViewApiDocs = { viewModel.updateState { copy(appState = AppState.API_DOCS) } },
                onTeleprompter = { viewModel.updateState { copy(appState = AppState.TELEPROMPTER) } },
                onAudioLibrary = { viewModel.updateState { copy(appState = AppState.AUDIO_LIBRARY) } },
                onAiAssistant = { viewModel.updateState { copy(appState = AppState.AI_ASSISTANT) } },
                onYouTubeStudio = { viewModel.updateState { copy(appState = AppState.YOUTUBE_STUDIO) } },
                onDeveloperDashboard = { viewModel.updateState { copy(appState = AppState.DEVELOPER_DASHBOARD) } },
                onLeaderboard = { viewModel.updateState { copy(appState = AppState.LEADERBOARD) } },
                onNavigateToTasteProfile = { viewModel.updateState { copy(appState = AppState.TASTE_PROFILE) } },
                onStudioGateway = { viewModel.updateState { copy(appState = AppState.STUDIO_GATEWAY) } },
                onPremiumUpgrade = { viewModel.updateState { copy(appState = AppState.PREMIUM_UPGRADE) } },
                onRewards = { viewModel.updateState { copy(appState = AppState.REWARDS) } },
                onKnowledgeHub = { viewModel.updateState { copy(appState = AppState.KNOWLEDGE_HUB) } },
                onContentGuard = { viewModel.updateState { copy(appState = AppState.CONTENT_GUARD) } },
                onQuranHub = { viewModel.updateState { copy(appState = AppState.HASANAT_HUB) } },
                onHadithStudio = { viewModel.updateState { copy(appState = AppState.HADITH_STUDIO) } },
                onVideoStyleCloner = { viewModel.updateState { copy(appState = AppState.VIDEO_STYLE_CLONER) } },
                onPhotoStudio = { viewModel.updateState { copy(appState = AppState.PHOTO_STUDIO) } },
                onStyleSelection = { viewModel.updateState { copy(appState = AppState.STYLE_SELECTION) } },
                onIslamicLibrary = { viewModel.updateState { copy(appState = AppState.ISLAMIC_LIBRARY) } },
                onAzkar = { viewModel.updateState { copy(appState = AppState.AZKAR) } },
                onPrayerTimes = { viewModel.updateState { copy(appState = AppState.PRAYER_TIMES) } },
                onScholarBiographies = { viewModel.updateState { copy(appState = AppState.SCHOLAR_BIOGRAPHIES) } },
                onProEditor = { viewModel.updateState { copy(appState = AppState.PRO_EDITOR) } },
                onMoneyPrinterTurbo = { viewModel.updateState { copy(appState = AppState.MONEY_PRINTER_TURBO) } },
                onVoiceStudio = { viewModel.updateState { copy(appState = AppState.VOICE_STUDIO) } },
                onMediaBrowser = { viewModel.updateState { copy(appState = AppState.MEDIA_BROWSER) } },
                onReels = { viewModel.updateState { copy(appState = AppState.REELS) } },
                onRestoreWorkspaceDraft = { viewModel.restoreWorkspaceDraft() },
                onRules = { viewModel.updateState { copy(appState = AppState.RULES) } },
                onAiStudioAgent = { viewModel.updateState { copy(appState = AppState.AI_STUDIO_AGENT) } },
                bottomBar = bottomNav
            )
        }
    }
}
