package com.qabas.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.SupabaseServices
import com.qabas.app.ui.components.ReelPlayer
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont
import com.qabas.app.ReelSafetyGuardrails
import com.qabas.app.ReelCrashBoundary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 🛰️ ReelsScreen — مشغل ومجتمع ريلز قبس الفضائي
 * النمط المعتمد: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 * تصميم متجاوب بدون فراغات علوية وبدون تكدس أو تداخل للعناصر
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Initialize Preferences
    LaunchedEffect(Unit) {
        ReelPreferencesManager.init(context)
    }

    // Collect Reel Settings
    val startMuted by ReelPreferencesManager.startMuted.collectAsState()
    var isAudioMuted by remember(startMuted) { mutableStateOf(startMuted) }
    val autoplayWifiOnly by ReelPreferencesManager.autoplayWifiOnly.collectAsState()
    val autoScrollOnFinish by ReelPreferencesManager.autoScrollOnFinish.collectAsState()
    val playbackSpeed by ReelPreferencesManager.playbackSpeed.collectAsState()
    val showCaptions by ReelPreferencesManager.showCaptions.collectAsState()
    val doubleTapAction by ReelPreferencesManager.doubleTapAction.collectAsState()
    val savedReels by ReelPreferencesManager.savedReels.collectAsState()
    val likedReels by ReelPreferencesManager.likedReels.collectAsState()
    val watchHistory by ReelPreferencesManager.watchHistory.collectAsState()

    var allReels by remember { mutableStateOf<List<SupabaseServices.ReelRow>>(emptyList()) }
    var hiddenReelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    // Top Navigation & Filter State
    var selectedTab by remember { mutableIntStateOf(0) } // 0: لك (For You), 1: متابعون (Following)
    var selectedCategory by remember { mutableStateOf("ALL") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Sheets State
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }
    var showDevChatScreen by remember { mutableStateOf(false) }
    var activeCommentReel by remember { mutableStateOf<SupabaseServices.ReelRow?>(null) }
    var activeMoreOptionsReel by remember { mutableStateOf<SupabaseServices.ReelRow?>(null) }
    var activeCreatorProfile by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var activeAudioReel by remember { mutableStateOf<SupabaseServices.ReelRow?>(null) }
    var followVersion by remember { mutableIntStateOf(0) }
    val viewer = remember(context, followVersion) { CreatorFollowManager.currentViewer(context) }

    // Interactive & Spatial Experience State
    var isKhushuMode by remember { mutableStateOf(false) }
    var showSoundscapeBar by remember { mutableStateOf(false) }
    var showTadabburCard by remember { mutableStateOf(false) }
    var repeatLoopCount by remember { mutableIntStateOf(1) }
    var particles by remember { mutableStateOf<List<FloatingParticle>>(emptyList()) }

    // Clean up soundscape on screen exit
    DisposableEffect(Unit) {
        onDispose {
            ReelAmbienceSoundscapeEngine.stop()
        }
    }

    // Graceful Back Handling (DevChat -> Sheets -> Search -> Home)
    BackHandler {
        when {
            showDevChatScreen -> showDevChatScreen = false
            showSoundscapeBar -> showSoundscapeBar = false
            showTadabburCard -> showTadabburCard = false
            isKhushuMode -> isKhushuMode = false
            activeAudioReel != null -> activeAudioReel = null
            activeCreatorProfile != null -> activeCreatorProfile = null
            showPublishDialog -> showPublishDialog = false
            showSettingsSheet -> showSettingsSheet = false
            showHistorySheet -> showHistorySheet = false
            activeCommentReel != null -> activeCommentReel = null
            activeMoreOptionsReel != null -> activeMoreOptionsReel = null
            isSearchActive -> {
                isSearchActive = false
                searchQuery = ""
            }
            else -> onBack()
        }
    }

    val likedReelIds = remember { mutableStateMapOf<String, Boolean>() }
    val dynamicLikeCounts = remember { mutableStateMapOf<String, Int>() }

    // User details for comments & cloud syncing
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val currentUserName = prefs.getString("user_name", "صانع محتوى قبس") ?: "صانع محتوى قبس"
    val currentUserEmail = prefs.getString("user_email", "guest@qabas.studio") ?: "guest@qabas.studio"
    val currentUserId = SupabaseServices.Auth.getCurrentUserId() ?: currentUserEmail

    val loadReels = {
        scope.launch {
            isLoading = true
            val remoteReels = runCatching {
                if (!ReelSafetyGuardrails.isSupabaseCallsAllowed()) {
                    emptyList()
                } else {
                    withTimeoutOrNull(7000L) {
                        SupabaseServices.Profiles.getActiveReels()
                    } ?: emptyList()
                }
            }.onSuccess {
                ReelSafetyGuardrails.recordNetworkSuccess()
            }.onFailure {
                ReelSafetyGuardrails.recordNetworkFailure()
            }.getOrDefault(emptyList())

            // Strict sanitization: drops malformed URLs, clamps numerical bounds, strips control characters
            val sanitized = remoteReels.mapNotNull { ReelSafetyGuardrails.validateAndSanitizeReel(it) }
            
            // Filter out any mock/dummy/test starter reels completely
            fun isDemoOrMockReel(r: SupabaseServices.ReelRow): Boolean {
                val id = r.id.lowercase()
                val url = r.videoUrl.lowercase()
                return id.startsWith("authentic_quran") ||
                    id.startsWith("starter_") ||
                    id.startsWith("mock_") ||
                    id.startsWith("demo_") ||
                    url.contains("mixkit.co") ||
                    r.userId == "qabas_holy_quran" ||
                    r.userId == "qabas_masjid" ||
                    r.userId == "qabas_tadabbur"
            }

            val finalReels = sanitized.filterNot { isDemoOrMockReel(it) }

            allReels = finalReels
            // Populate likes & counts from real database rows
            allReels.forEach { r ->
                dynamicLikeCounts[r.id] = r.likesCount
                likedReelIds[r.id] = likedReels.any { it.reelId == r.id }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadReels()
    }

    // Filter reels based on Tab, Category, Search Query and Hidden status
    val displayReels = remember(allReels, hiddenReelIds, selectedTab, selectedCategory, searchQuery, followVersion) {
        allReels.filterNot { hiddenReelIds.contains(it.id) }
            .filter { reel ->
                when (selectedCategory) {
                    "QURAN" -> reel.tags.contains("قرآن") || reel.title.contains("قرآن") || reel.title.contains("تلاوة")
                    "HADITH" -> reel.tags.contains("حديث") || reel.title.contains("حديث") || reel.title.contains("سنة")
                    "STORIES" -> reel.tags.contains("قصص") || reel.title.contains("قصة") || reel.title.contains("عبرة")
                    else -> true
                }
            }
            .filter { reel ->
                if (searchQuery.isBlank()) true
                else {
                    reel.title.contains(searchQuery, ignoreCase = true) ||
                    (reel.userName?.contains(searchQuery, ignoreCase = true) == true) ||
                    (reel.description?.contains(searchQuery, ignoreCase = true) == true) ||
                    reel.tags.contains(searchQuery)
                }
            }
            .filter { reel ->
                if (selectedTab == 1) {
                    // Following tab: show reels by followed creators or own published reels
                    val followedIds = CreatorFollowManager.followedIds(context)
                    followedIds.any { it.equals(reel.userId, ignoreCase = true) } ||
                        CreatorFollowManager.identitiesMatch(
                            reel.userId,
                            reel.userName,
                            viewer.id,
                            viewer.displayName,
                            viewer.email
                        )
                } else true
            }
    }

    val pagerState = rememberPagerState(pageCount = { displayReels.size })

    // Track Watch History when page settles
    LaunchedEffect(pagerState.currentPage, displayReels) {
        if (displayReels.isNotEmpty() && pagerState.currentPage < displayReels.size) {
            val currentReel = displayReels[pagerState.currentPage]
            ReelPreferencesManager.recordWatch(
                context = context,
                reelId = currentReel.id,
                title = currentReel.title,
                userName = currentReel.userName,
                userAvatar = currentReel.userAvatar,
                videoUrl = currentReel.videoUrl,
                category = selectedCategory,
                durationSec = 2
            )
        }
    }

    ReelCrashBoundary(onSafeExit = onBack) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // Full bleed without empty top gap
            bottomBar = bottomBar,
            containerColor = Color.Black
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = GoldPrimary, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "جاري الاتصال بمجتمع قبس الإيماني...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontFamily = CairoFont,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (allReels.isEmpty()) {
                    // Empty state: geometric balance (gradient void + weighted centering + capped width)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF0B1324), Color.Black, Color(0xFF070B14))
                                )
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    ) {
                        // Faint watermark emblem filling the void
                        Icon(
                            imageVector = Icons.Default.SlowMotionVideo,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.04f),
                            modifier = Modifier
                                .size(280.dp)
                                .align(Alignment.Center)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 420.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFF0F172A).copy(alpha = 0.92f))
                                    .border(BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)), RoundedCornerShape(24.dp))
                                    .padding(24.dp)
                            ) {
                            Surface(
                                shape = CircleShape,
                                color = GoldPrimary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.VideoCall,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "لا توجد مقاطع منشورة حالياً",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "كن أول من ينشر مقطعاً قرآنياً أو دعوياً مباركاً ليظهر لجميع المسلمين حول العالم ✨",
                                fontFamily = CairoFont,
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { showPublishDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepSlate),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("نشر مقطع الآن", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { loadReels() },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تحديث", fontFamily = CairoFont, fontSize = 12.5.sp, maxLines = 1)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1.2f))
                    }
                }
            } else {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        val reel = displayReels[page]
                        val isPageVisible = pagerState.currentPage == page
                        val isLiked = likedReelIds[reel.id] == true
                        val isSaved = savedReels.any { it.reelId == reel.id }
                        val currentLikes = dynamicLikeCounts[reel.id] ?: reel.likesCount

                        val handleToggleLike: () -> Unit = {
                            if (ReelSafetyGuardrails.canPerformLike(reel.id)) {
                                val newLikedState = !isLiked
                                likedReelIds[reel.id] = newLikedState
                                dynamicLikeCounts[reel.id] = if (newLikedState) currentLikes + 1 else (currentLikes - 1).coerceAtLeast(0)

                                ReelPreferencesManager.toggleLiked(
                                    context = context,
                                    reelId = reel.id,
                                    title = reel.title,
                                    userName = reel.userName,
                                    userAvatar = reel.userAvatar,
                                    videoUrl = reel.videoUrl,
                                    category = selectedCategory
                                )

                                scope.launch {
                                    runCatching {
                                        withTimeoutOrNull(5000L) {
                                            if (newLikedState) {
                                                SupabaseServices.Profiles.likeReel(reel.id, currentUserId)
                                            } else {
                                                SupabaseServices.Profiles.unlikeReel(reel.id, currentUserId)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val handleToggleSave: () -> Unit = {
                            val nowSaved = ReelPreferencesManager.toggleSave(
                                context = context,
                                reelId = reel.id,
                                title = reel.title,
                                userName = reel.userName,
                                userAvatar = reel.userAvatar,
                                videoUrl = reel.videoUrl,
                                category = selectedCategory
                            )
                            Toast.makeText(
                                context,
                                if (nowSaved) "تم حفظ المقطع في المحفوظات 🔖" else "تمت إزالة المقطع من المحفوظات",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        val isNetworkPermitted = remember(autoplayWifiOnly) {
                            ReelSafetyGuardrails.isNetworkPermittedForAutoplay(context, autoplayWifiOnly)
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            ReelPlayer(
                                videoUrl = reel.videoUrl,
                                isPlaying = isPageVisible && isNetworkPermitted,
                                playbackSpeed = playbackSpeed,
                                initialMuted = startMuted,
                                isMuted = isAudioMuted,
                                onVideoEnded = if (autoScrollOnFinish && page < displayReels.size - 1) {
                                    {
                                        scope.launch {
                                            pagerState.animateScrollToPage(page + 1)
                                        }
                                    }
                                } else null,
                                onDoubleTapLike = {
                                    when (doubleTapAction) {
                                        "SAVE" -> handleToggleSave()
                                        else -> if (!isLiked) handleToggleLike()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Top subtle gradient for header readability
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                                        )
                                    )
                            )

                            // Bottom subtle gradient for metadata readability
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                            )

                            // Overlays & Actions
                            ReelSpatialHUDOverlays(
                                reel = reel,
                                isLiked = isLiked,
                                isSaved = isSaved,
                                showCaptions = showCaptions,
                                likesCount = currentLikes,
                                isKhushuMode = isKhushuMode,
                                onToggleKhushu = { isKhushuMode = !isKhushuMode },
                                onReactionTriggered = { reaction ->
                                    val newParticle = FloatingParticle(
                                        id = System.currentTimeMillis() + (0..1000).random(),
                                        text = "${reaction.icon} ${reaction.text}",
                                        offsetX = (30..220).random().toFloat(),
                                        startY = 600f
                                    )
                                    particles = particles + newParticle
                                    ReelPreferencesManager.triggerHaptic(context)
                                    Toast.makeText(context, "بارك الله فيك! ${reaction.text} (+${reaction.rewardHasanat} حسنات) 🌟", Toast.LENGTH_SHORT).show()
                                },
                                onLike = handleToggleLike,
                                onSave = handleToggleSave,
                                onComment = {
                                    activeCommentReel = reel
                                },
                                onAudioClick = {
                                    activeAudioReel = reel
                                },
                                onShare = {
                                    runCatching {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "شاهد مقطع «${reel.title}» المميز عبر استوديو قبس: ${reel.videoUrl}"
                                            )
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "مشاركة الريلز")
                                        context.startActivity(shareIntent)
                                        ReelPreferencesManager.recordShare(context)
                                    }
                                },
                                onMoreOptions = {
                                    activeMoreOptionsReel = reel
                                },
                                onOpenCreatorProfile = { cId, cName, cAvatar ->
                                    activeCreatorProfile = Triple(cId, cName, cAvatar)
                                }
                            )
                        }
                    }
                }

                // Floating Particles Layer
                FloatingParticlesLayer(
                    particles = particles,
                    onParticleFinished = { pId ->
                        particles = particles.filterNot { it.id == pId }
                    }
                )

                // Top Spatial HUD Header (Clean, minimal, no empty space)
                if (!isKhushuMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Button in circular frosted glass pill
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A).copy(alpha = 0.75f))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Center Cybernetic Tabs Capsule (متابعون | لك ✦) — neatly fitted without truncation
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xFF0F172A).copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                ) {
                                    // For You Tab
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (selectedTab == 0) GoldPrimary.copy(alpha = 0.22f) else Color.Transparent)
                                            .border(
                                                BorderStroke(1.dp, if (selectedTab == 0) GoldPrimary.copy(alpha = 0.6f) else Color.Transparent),
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                selectedTab = 0
                                                ReelPreferencesManager.triggerHaptic(context)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "لك ✦",
                                            fontFamily = CairoFont,
                                            fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            softWrap = false,
                                            maxLines = 1,
                                            color = if (selectedTab == 0) GoldPrimary else Color.White.copy(alpha = 0.7f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(3.dp))

                                    // Following Tab
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (selectedTab == 1) GoldPrimary.copy(alpha = 0.22f) else Color.Transparent)
                                            .border(
                                                BorderStroke(1.dp, if (selectedTab == 1) GoldPrimary.copy(alpha = 0.6f) else Color.Transparent),
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                selectedTab = 1
                                                ReelPreferencesManager.triggerHaptic(context)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "متابعون",
                                            fontFamily = CairoFont,
                                            fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            softWrap = false,
                                            maxLines = 1,
                                            color = if (selectedTab == 1) GoldPrimary else Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            // Left Spatial Actions Capsule (Mute + Publish + Search + Settings)
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xFF0F172A).copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    val isDeveloper = remember(currentUserEmail) {
                                        ReelPublishManager.isDeveloperUser(context, currentUserEmail)
                                    }

                                    // Sound Mute Toggle
                                    IconButton(
                                        onClick = {
                                            isAudioMuted = !isAudioMuted
                                            ReelPreferencesManager.triggerHaptic(context)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = if (isAudioMuted) "تشغيل الصوت" else "كتم الصوت",
                                            tint = if (isAudioMuted) Color(0xFFEF4444) else Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    // Publish button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isDeveloper) GoldPrimary else GoldPrimary.copy(alpha = 0.18f))
                                            .border(BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.6f)), RoundedCornerShape(14.dp))
                                            .clickable {
                                                showPublishDialog = true
                                                ReelPreferencesManager.triggerHaptic(context)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isDeveloper) Icons.Default.Verified else Icons.Default.Add,
                                                contentDescription = "نشر",
                                                tint = if (isDeveloper) DeepSlate else GoldPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (isDeveloper) "نشر 👑" else "نشر",
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                softWrap = false,
                                                maxLines = 1,
                                                color = if (isDeveloper) DeepSlate else GoldPrimary
                                            )
                                        }
                                    }

                                    // Search Icon
                                    IconButton(
                                        onClick = {
                                            isSearchActive = !isSearchActive
                                            ReelPreferencesManager.triggerHaptic(context)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "بحث",
                                            tint = if (isSearchActive) GoldPrimary else Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    // Settings / History Icon
                                    IconButton(
                                        onClick = {
                                            showSettingsSheet = true
                                            ReelPreferencesManager.triggerHaptic(context)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (savedReels.isNotEmpty()) {
                                                    Badge(
                                                        containerColor = GoldPrimary,
                                                        contentColor = DeepSlate
                                                    ) {
                                                        Text(
                                                            text = "${savedReels.size}",
                                                            fontSize = 8.sp,
                                                            fontFamily = RobotoMonoFont
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "إعدادات",
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Expandable Minimalist Search & Categories
                        AnimatedVisibility(
                            visible = isSearchActive,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A).copy(alpha = 0.95f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text("ابحث عن تلاوة، شيخ، موضوع أو عنوان...", fontFamily = CairoFont, fontSize = 12.sp, color = Color.Gray)
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.White)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val categories = listOf(
                                        "ALL" to "الكل 🌟",
                                        "QURAN" to "تلاوات قرآنية 🌿",
                                        "HADITH" to "أحاديث نبوية ⚡",
                                        "STORIES" to "قصص وعبر 🛡️"
                                    )
                                    items(categories) { (key, label) ->
                                        val isSelected = selectedCategory == key
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.08f),
                                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.15f)),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    selectedCategory = key
                                                    ReelPreferencesManager.triggerHaptic(context)
                                                }
                                        ) {
                                            Text(
                                                text = label,
                                                fontFamily = CairoFont,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) DeepSlate else Color.White,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Minimal Khushu Mode Exit Indicator
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(top = 12.dp)
                            .clickable { isKhushuMode = false }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(text = "🌙", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "وضع الخشوع مفعل (انقر للرجوع للوضع العادي)",
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    // Comments Bottom Sheet
    activeCommentReel?.let { reel ->
        ReelCommentsBottomSheet(
            reel = reel,
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            onDismiss = { activeCommentReel = null }
        )
    }

    // More Options Bottom Sheet
    activeMoreOptionsReel?.let { reel ->
        val isSaved = savedReels.any { it.reelId == reel.id }
        ReelMoreOptionsSheet(
            reel = reel,
            isSaved = isSaved,
            currentUserEmail = currentUserEmail,
            currentUserId = currentUserId,
            onToggleSave = {
                ReelPreferencesManager.toggleSave(
                    context = context,
                    reelId = reel.id,
                    title = reel.title,
                    userName = reel.userName,
                    userAvatar = reel.userAvatar,
                    videoUrl = reel.videoUrl,
                    category = selectedCategory
                )
            },
            onSpeedSelected = { newSpeed ->
                ReelPreferencesManager.setPlaybackSpeed(context, newSpeed)
                Toast.makeText(context, "تم ضبط سرعة التشغيل على ${newSpeed}x", Toast.LENGTH_SHORT).show()
            },
            onNotInterested = {
                hiddenReelIds = hiddenReelIds + reel.id
            },
            onDeleteReel = {
                scope.launch {
                    val ok = ReelPublishManager.deleteReel(context, currentUserEmail, currentUserId, reel)
                    if (ok) {
                        Toast.makeText(context, "تم حذف المقطع بنجاح 🗑️", Toast.LENGTH_SHORT).show()
                        allReels = allReels.filterNot { it.id == reel.id }
                        activeMoreOptionsReel = null
                    } else {
                        Toast.makeText(context, "تعذر حذف المقطع، يرجى التحقق من الصلاحيات والاتصال", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReportToDevChat = { reason, details ->
                scope.launch {
                    DevChatRepository.reportOffensiveReel(
                        context = context,
                        reporterEmail = currentUserEmail,
                        reporterName = currentUserName,
                        reelId = reel.id,
                        reelTitle = reel.title,
                        reelCreator = reel.userName ?: "صانع المحتوى",
                        reelVideoUrl = reel.videoUrl,
                        reason = reason,
                        additionalNotes = details
                    )
                    Toast.makeText(context, "تم إرسال بلاغك لمحادثة المطور مباشرة 🛡️", Toast.LENGTH_LONG).show()
                    showDevChatScreen = true
                }
            },
            onOpenDevChat = {
                showDevChatScreen = true
            },
            onDismiss = { activeMoreOptionsReel = null }
        )
    }

    // Publish Reel Dialog
    if (showPublishDialog) {
        ReelPublishDialog(
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            currentUserEmail = currentUserEmail,
            onDismiss = { showPublishDialog = false },
            onPublishedSuccess = {
                loadReels()
            }
        )
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        ReelSettingsSheet(
            onDismiss = { showSettingsSheet = false }
        )
    }

    // History & Activity Bottom Sheet
    if (showHistorySheet) {
        ReelHistorySheet(
            onDismiss = { showHistorySheet = false },
            onSelectReel = { targetReelId ->
                val targetIndex = displayReels.indexOfFirst { it.id == targetReelId }
                if (targetIndex >= 0) {
                    scope.launch {
                        pagerState.scrollToPage(targetIndex)
                    }
                } else {
                    val inAllIndex = allReels.indexOfFirst { it.id == targetReelId }
                    if (inAllIndex >= 0) {
                        hiddenReelIds = hiddenReelIds - targetReelId
                        selectedCategory = "ALL"
                        searchQuery = ""
                        scope.launch {
                            pagerState.scrollToPage(inAllIndex)
                        }
                    }
                }
            }
        )
    }

    // Creator Profile Bottom Sheet
    activeCreatorProfile?.let { (id, name, avatar) ->
        ReelCreatorProfileSheet(
            creatorId = id,
            creatorName = name,
            creatorAvatar = avatar,
            allReels = allReels,
            onSelectReel = { targetId ->
                val idx = displayReels.indexOfFirst { it.id == targetId }
                if (idx >= 0) {
                    scope.launch { pagerState.scrollToPage(idx) }
                }
            },
            onDismiss = { activeCreatorProfile = null },
            viewer = viewer,
            onOpenMyProfile = {
                activeCreatorProfile = null
                onOpenProfile()
            },
            onFollowChanged = { followVersion++ }
        )
    }

    // Audio / Recitation Details Bottom Sheet
    activeAudioReel?.let { reel ->
        ReelAudioDetailsSheet(
            reel = reel,
            onDismiss = { activeAudioReel = null }
        )
    }

    // Direct Dev Chat Overlay
    if (showDevChatScreen) {
        DevChatScreen(
            userEmail = currentUserEmail,
            onBack = { showDevChatScreen = false }
        )
    }
}

/**
 * 🛰️ ReelSpatialHUDOverlays — طبقة تفاصيل المقطع وأزرار التفاعل بالنمط المكاني الخفيف
 */
@Composable
fun ReelSpatialHUDOverlays(
    reel: SupabaseServices.ReelRow,
    isLiked: Boolean,
    isSaved: Boolean,
    showCaptions: Boolean,
    likesCount: Int,
    isKhushuMode: Boolean,
    onToggleKhushu: () -> Unit,
    onReactionTriggered: (SpiritualReaction) -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onComment: () -> Unit,
    onAudioClick: () -> Unit,
    onShare: () -> Unit,
    onMoreOptions: () -> Unit,
    onOpenCreatorProfile: (creatorId: String, creatorName: String, creatorAvatar: String?) -> Unit
) {
    // Disc rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    if (isKhushuMode) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Bottom Meta Info (Creator profile, description, sound, spiritual reactions)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 74.dp, bottom = 64.dp)
                .fillMaxWidth()
        ) {
            // Micro Spiritual Reactions Bar (Horizontal Scroll, strictly non-overlapping)
            LazyRow(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val microReactions = listOf(
                    SpiritualReaction("subhanallah", "سبحان الله", "💫"),
                    SpiritualReaction("alhamdulillah", "الحمد لله", "🌿"),
                    SpiritualReaction("mashallah", "ما شاء الله", "🌟"),
                    SpiritualReaction("jazakallah", "أجر 🤍", "")
                )
                items(microReactions) { rx ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onReactionTriggered(rx) }
                    ) {
                        Text(
                            text = if (rx.icon.isNotEmpty()) "${rx.icon} ${rx.text}" else rx.text,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Creator Tag (Clickable with adaptive weight distribution)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onOpenCreatorProfile(
                            reel.userId,
                            reel.userName ?: "صانع محتوى قبس",
                            reel.userAvatar
                        )
                    }
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                AsyncImage(
                    model = reel.userAvatar ?: "https://ui-avatars.com/api/?name=${reel.userName}&background=D4AF37&color=0B0F19",
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.2.dp, GoldPrimary, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = reel.userName ?: "صانع محتوى قبس",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Surface(
                        color = GoldPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(13.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "موثق",
                            tint = DeepSlate,
                            modifier = Modifier.padding(1.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "مفحوص شرعياً 🌿",
                            fontFamily = CairoFont,
                            fontSize = 9.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                text = reel.title,
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description / Captions (if enabled)
            if (showCaptions && !reel.description.isNullOrBlank()) {
                Text(
                    text = reel.description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Audio track badge
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAudioClick() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "صوت أصلي معتمد // استوديو قبس",
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = CairoFont,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Right Action Bar (Sleek, 5 items + Vinyl Disc)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Like Button
            ReelActionPill(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = formatCount(likesCount),
                tint = if (isLiked) Color(0xFFEF4444) else Color.White,
                onClick = onLike
            )

            // Comment Button
            ReelActionPill(
                icon = Icons.Default.Comment,
                label = formatCount(reel.commentsCount),
                tint = Color.White,
                onClick = onComment
            )

            // Bookmark / Save Button
            ReelActionPill(
                icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = if (isSaved) "محفوظ" else "حفظ",
                tint = if (isSaved) GoldPrimary else Color.White,
                onClick = onSave
            )

            // Share Button
            ReelActionPill(
                icon = Icons.Default.Share,
                label = formatCount(reel.sharesCount),
                tint = Color.White,
                onClick = onShare
            )

            // More Options (...)
            ReelActionPill(
                icon = Icons.Default.MoreHoriz,
                label = "المزيد",
                tint = Color.White.copy(alpha = 0.85f),
                onClick = onMoreOptions
            )

            // Spinning Vinyl Disc
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .rotate(rotation)
                    .clickable { onAudioClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(GoldPrimary)
                )
            }
        }
    }
}

/**
 * زر تفاعلي فضائي شفاف بحجم لمس قياسي وتصميم أنيق
 */
@Composable
fun ReelActionPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A).copy(alpha = 0.55f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontFamily = CairoFont,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsBottomSheet(
    reel: SupabaseServices.ReelRow,
    currentUserId: String,
    currentUserName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var commentText by remember { mutableStateOf("") }
    var commentsList by remember { mutableStateOf<List<SupabaseServices.ReelCommentRow>>(emptyList()) }
    var isPosting by remember { mutableStateOf(false) }

    LaunchedEffect(reel.id) {
        commentsList = SupabaseServices.Profiles.getReelComments(reel.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .fillMaxHeight(0.6f)
        ) {
            Text(
                text = "التعليقات (${commentsList.size})",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            if (commentsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "كن أول من يشارك بتعليق ملهم 💬",
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = CairoFont,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(commentsList) { c ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = c.userId.take(1).uppercase(),
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "@${c.userId.take(10)}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontFamily = RobotoMonoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = c.commentText,
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = {
                        Text("اكتب تعليقاً طيباً...", fontFamily = CairoFont, fontSize = 12.sp, color = Color.Gray)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val sanitized = ReelSafetyGuardrails.sanitizeCommentText(commentText)
                        if (sanitized != null && !isPosting) {
                            if (!ReelSafetyGuardrails.canPostComment()) {
                                Toast.makeText(context, "يرجى الانتظار قليلاً قبل إرسال تعليق آخر ⏱️", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            isPosting = true
                            coroutineScope.launch {
                                val ok = runCatching {
                                    withTimeoutOrNull(6000L) {
                                        SupabaseServices.Profiles.addReelComment(reel.id, currentUserId, sanitized)
                                    } ?: false
                                }.getOrDefault(false)
                                if (ok) {
                                    commentsList = commentsList + SupabaseServices.ReelCommentRow(
                                        id = "local_${System.currentTimeMillis()}",
                                        reelId = reel.id,
                                        userId = currentUserName,
                                        commentText = sanitized
                                    )
                                    commentText = ""
                                } else {
                                    Toast.makeText(context, "تعذر إرسال التعليق حالياً، يرجى المحاولة لاحقاً", Toast.LENGTH_SHORT).show()
                                }
                                isPosting = false
                            }
                        }
                    },
                    enabled = commentText.isNotBlank() && !isPosting,
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (commentText.isNotBlank()) GoldPrimary else Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        tint = if (commentText.isNotBlank()) DeepSlate else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
