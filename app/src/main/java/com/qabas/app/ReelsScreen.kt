package com.qabas.app

import android.app.Activity
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.SupabaseServices
import com.qabas.app.ui.components.ReelPlayer
import com.qabas.app.ui.components.GeometricToolsDock
import com.qabas.app.ui.components.DockTool
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.AmiriFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.RobotoMonoFont
import com.qabas.app.ReelSafetyGuardrails
import com.qabas.app.ReelCrashBoundary
import com.qabas.app.SilentReelPrefetcher
import com.qabas.app.SilentReelStorageManager
import com.qabas.app.ui.components.SilentSyncDevFloatingHud
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** حجم صفحة خلاصة الريلز — أساس التمرير اللانهائي */
private const val REELS_PAGE_SIZE = 20

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

/** يحوّل تاريخ النشر (ISO) إلى نص نسبي مثل «منذ ٣ أيام». */
private fun formatRelativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return ""
    val minutes = java.time.Duration.between(instant, java.time.Instant.now()).toMinutes()
    return when {
        minutes < 1L -> "الآن"
        minutes < 60L -> "منذ $minutes دقيقة"
        minutes < 1440L -> "منذ ${minutes / 60} ساعة"
        minutes < 10080L -> "منذ ${minutes / 1440} يوم"
        minutes < 43200L -> "منذ ${minutes / 10080} أسبوع"
        minutes < 525600L -> "منذ ${minutes / 43200} شهر"
        else -> "منذ ${minutes / 525600} سنة"
    }
}

/**
 * 🌟 خلاصة المقاطع الأصلية الجاهزة (Authentic Starter Reels Feed)
 * تلاوات قرآنية وتدبرات إسلامية متوافقة مع ExoPlayer تعمل للضيف وفي حال انقطاع الشبكة
 */
private val AUTHENTIC_STARTER_REELS: List<SupabaseServices.ReelRow> = listOf(
    SupabaseServices.ReelRow(
        id = "qabas_quran_recitation_starter",
        userId = "qabas_reciter_1",
        userName = "تلاوات خاشعة 🌿",
        userAvatar = "https://images.unsplash.com/photo-1544717305-2782549b5136?w=200&q=80",
        title = "﴿الَّذِينَ آمَنُوا وَتَطْمَئِنُّ قُلُوبُهُم بِذِكْرِ اللَّهِ﴾ 🌿 تلاوة خاشعة وسكينة",
        description = "تلاوة قرآنية مباركة تأخذ القلب إلى آفاق الطمأنينة والسكينة في رحاب كلام الله تبارك وتعالى.",
        // مستضاف في سحابة قبس نفسها — لا اعتماد على مزودي خارجيين يحجبون الروابط
        videoUrl = "https://egkgygbvoeevakwkbvfu.supabase.co/storage/v1/object/public/media-assets/starter_reels/quran_recitation.mp4",
        thumbnailUrl = "https://images.unsplash.com/photo-1609599006353-e629aaabfeae?w=600&q=80",
        tags = listOf("قرآن", "تلاوة", "خشوع", "سكينة", "ذكر"),
        likesCount = 1420,
        commentsCount = 85,
        sharesCount = 312,
        createdAt = "2025-01-01T12:00:00Z"
    ),
    SupabaseServices.ReelRow(
        id = "qabas_faith_lecture_starter",
        userId = "qabas_reciter_2",
        userName = "مشكاة الحكمة والموعظة 📜",
        userAvatar = "https://images.unsplash.com/photo-1548013146-72479768bada?w=200&q=80",
        title = "هادم اللذات 📜 وقفة صادقة مع الآخرة وزوال الدنيا",
        description = "موعظة مؤثرة تدق أبواب القلوب: تذكير بزوال الدنيا وسرعة الرحيل إلى الله سبحانه وتعالى.",
        // مستضاف في سحابة قبس نفسها — لا اعتماد على مزودي خارجيين يحجبون الروابط
        videoUrl = "https://egkgygbvoeevakwkbvfu.supabase.co/storage/v1/object/public/media-assets/starter_reels/faith_lecture.mp4",
        thumbnailUrl = "https://images.unsplash.com/photo-1548013146-72479768bada?w=600&q=80",
        tags = listOf("مواعظ", "تذكير", "آخرة", "زهد", "قلب"),
        likesCount = 2210,
        commentsCount = 130,
        sharesCount = 470,
        createdAt = "2025-01-02T15:30:00Z"
    )
)

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
    var audioVolume by remember { mutableFloatStateOf(if (startMuted) 0f else 1f) }
    val isAudioMuted = audioVolume <= 0.01f
    val autoplayWifiOnly by ReelPreferencesManager.autoplayWifiOnly.collectAsState()
    val autoScrollOnFinish by ReelPreferencesManager.autoScrollOnFinish.collectAsState()
    val playbackSpeed by ReelPreferencesManager.playbackSpeed.collectAsState()
    val showCaptions by ReelPreferencesManager.showCaptions.collectAsState()
    val doubleTapAction by ReelPreferencesManager.doubleTapAction.collectAsState()
    val savedReels by ReelPreferencesManager.savedReels.collectAsState()
    val likedReels by ReelPreferencesManager.likedReels.collectAsState()
    val watchHistory by ReelPreferencesManager.watchHistory.collectAsState()
    val alAtharEnabled by ReelPreferencesManager.alAtharEnabled.collectAsState()

    var allReels by remember { mutableStateOf<List<SupabaseServices.ReelRow>>(emptyList()) }
    var hiddenReelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableFloatStateOf(0.12f) }
    // فشل الشبكة (يُميَّز عن القائمة الفارغة فعلاً) — يعرض حالة خطأ مع إعادة محاولة
    var loadError by remember { mutableStateOf(false) }
    // سجل التشخيص الفني الأخير لجلب المقاطع من السحابة
    var lastDiagnostic by remember { mutableStateOf<SupabaseServices.ReelsFetchDiagnosticResult?>(null) }
    var isRetrying by remember { mutableStateOf(false) }
    // وضع تصفح المحفوظات محلياً عند انقطاع الإنترنت (Offline-First)
    var isOfflineMode by remember { mutableStateOf(false) }
    var isAlAtharMode by remember { mutableStateOf(false) }

    // ترقيم صفحات: تحميل المزيد عند الاقتراب من نهاية القائمة (بدون كتلة إنزال تقليدي)
    var canLoadMore by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    // إزاحة الخادم الفعلية: عدد الصفوف الخام المجلوبة عن بُعد — مستقلة عن القائمة المعروضة المفلترة
    var remoteOffset by remember { mutableStateOf(0) }
    val requestGeneration = remember { java.util.concurrent.atomic.AtomicInteger(0) }

    // المحفوظات السحابية (reel_bookmarks) — تُزامن حالة «محفوظ» عبر أجهزة المستخدم
    var cloudBookmarks by remember { mutableStateOf<Set<String>>(emptySet()) }

    // قياس مدة المشاهدة الفعلية لكل مقطع (ثواني تشغيل حقيقية لا وقت مرور)
    // خريطة حالة تشغيل لكل صفحة — لا تتسرب حالة صفحة لأخرى عند التمرير السريع
    val pagePlayingStates = remember { mutableStateMapOf<Int, Boolean>() }
    // مدة المشاهدة الفعلية المتراكمة لكل مقطع (ثواني تشغيل حقيقية)
    val watchSeconds = remember { mutableStateMapOf<String, Int>() }
    var lastWatchedReelId by remember { mutableStateOf<String?>(null) }

    // أقفال منع تكرار الطلب لكل مقطع (إعجاب) حتى لا تتعارض نقرات متسارعة
    val likeInFlight = remember { mutableStateMapOf<String, Boolean>() }

    // Top Navigation & Filter State
    var selectedTab by remember { mutableIntStateOf(0) } // 0: لك (For You), 1: متابعون (Following), 2: الأثر (Al-Athar)
    var selectedCategory by remember { mutableStateOf("ALL") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var showAlAtharExplanation by remember { mutableStateOf(false) }

    // Sheets State
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }
    var showDevChatScreen by remember { mutableStateOf(false) }
    var activeDetailsReel by remember { mutableStateOf<SupabaseServices.ReelRow?>(null) }
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
    // عدّاد التعليقات المُضافة جالسياً بعد نشر تعليق ناجح (يدمج مع عدّاد الصف)
    var commentBumps by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    // Immersive chrome (bottom bar) visibility: hidden on entry with a smooth slide,
    // auto-hides after 10s of inactivity, revealed by tapping the video or the handle.
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(350)
        chromeVisible = false
    }
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(10_000)
            chromeVisible = false
        }
    }

    // Geometric tools dock (all screen tools consolidated in one collapsible drawer)
    var dockExpanded by remember { mutableStateOf(false) }
    // ⚡ محرك الجلب المسبق الصامت (لوضع الأثر)
    LaunchedEffect(Unit) {
        SilentReelPrefetcher.startSync(context, scope)
    }

    LaunchedEffect(alAtharEnabled) {
        if (!alAtharEnabled && selectedTab == 2) {
            selectedTab = 0
            isAlAtharMode = false
        }
    }

    LaunchedEffect(dockExpanded) {
        if (dockExpanded) {
            delay(12_000)
            dockExpanded = false
        }
    }

    // Loop the current reel (working) instead of auto-advancing
    var loopCurrentReel by remember { mutableStateOf(false) }

    // Screen brightness, adjusted by vertical swipe on the left half of the video
    var screenBrightness by remember {
        val activity = context as? Activity
        val current = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (current in 0f..1f) current else 0.5f)
    }
    LaunchedEffect(screenBrightness) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val attrs = activity.window.attributes
        attrs.screenBrightness = screenBrightness
        activity.window.attributes = attrs
    }

    // Clean up soundscape on screen exit
    DisposableEffect(Unit) {
        onDispose {
            ReelAmbienceSoundscapeEngine.stop()
        }
    }

    // دورة الحياة: إيقاف التشغيل عند خروج التطبيق للخلفية (ON_STOP) واستئنافه عند العودة —
    // لا يستمر صوت الفيديو في الخلفية أبداً (سلوك تيك توك/انستغرام)
    var isScreenActive by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isScreenActive = true
                Lifecycle.Event.ON_STOP -> isScreenActive = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
    // 🛡️ User ID Selection - CRITICAL for RLS
    // For reads/display, we can fallback. For WRITES (Like/Save/Publish), we MUST use Supabase ID.
    val currentSupabaseId = SupabaseServices.Auth.getCurrentUserId()
    val currentUserId = currentSupabaseId
        ?: prefs.getString("user_id", null)?.takeIf { it.isNotBlank() }
        ?: FirebaseAuthRepository.getEffectiveUserId(context)
        ?: currentUserEmail

    val isDeveloper = remember(currentUserEmail) {
        ReelPublishManager.isDeveloperUser(context, currentUserEmail) ||
        prefs.getBoolean("is_developer", false) ||
        prefs.getBoolean("is_admin", false) ||
        CloudServices.isOwnerAccount(currentUserEmail)
    }

    // 🛡️ فحص دقيق للجلسة في Firebase Auth والتطبيق لمنع الضيف فقط وليس الحسابات المسجلة
    val fbUser by FirebaseAuthRepository.create(context).authStateFlow().collectAsState(initial = FirebaseAuthRepository.create(context).currentUser)
    val isGuest = remember(fbUser, isDeveloper) {
        FirebaseAuthRepository.isGuest(context)
    }
    var showGuestLoginPrompt by remember { mutableStateOf(false) }

    /**
     * 🛡️ Guest Interaction Gatekeeper
     * Intercepts actions for guests and shows login prompt.
     */
    fun checkGuestInteraction(action: () -> Unit) {
        if (isGuest) {
            showGuestLoginPrompt = true
        } else {
            action()
        }
    }

    /**
     * إثبات المشاهدة: تسجيل محلي + سحابي (للمستخدم المصادق فقط) بمدة المشاهدة الفعلية المقيسة.
     */
    fun finalizeWatch(reelId: String?) {
        val rid = reelId ?: return
        val secs = watchSeconds[rid] ?: 0
        if (secs <= 0) return
        val reel = allReels.find { it.id == rid } ?: return
        ReelPreferencesManager.recordWatch(
            context = context,
            reelId = rid,
            title = reel.title,
            userName = reel.userName,
            userAvatar = reel.userAvatar,
            videoUrl = reel.videoUrl,
            category = selectedCategory,
            durationSec = secs
        )
        val authId = FirebaseAuthRepository.getEffectiveUserId(context) ?: SupabaseServices.Auth.getCurrentUserId()
        if (authId != null && ReelSafetyGuardrails.isSupabaseCallsAllowed()) {
            scope.launch {
                runCatching { SupabaseServices.Profiles.recordReelWatch(rid, authId, secs) }
            }
        }
    }

    fun loadReels(isManualRetry: Boolean = false) {
        scope.launch {
            if (isManualRetry) isRetrying = true
            isLoading = true
            loadError = false
            loadingProgress = 0.15f
            val gen = requestGeneration.incrementAndGet()
            loadingProgress = 0.35f

            if (isManualRetry) {
                ReelSafetyGuardrails.forceResetCircuitBreaker()
            }

            loadingProgress = 0.55f
            val diagResult = withTimeoutOrNull(9000L) {
                SupabaseServices.Profiles.getActiveReelsDiagnosed(
                    limit = REELS_PAGE_SIZE,
                    offset = 0,
                    forceBypassCircuitBreaker = isManualRetry
                )
            } ?: SupabaseServices.ReelsFetchDiagnosticResult(
                reels = null,
                isSuccess = false,
                httpCode = 408,
                latencyMs = 9000L,
                errorStage = "TIMEOUT",
                errorMessage = "انتهت مهلة استجابة السحابة (9 ثوانٍ)"
            )

            lastDiagnostic = diagResult
            val remoteReels = diagResult.reels
            val fetchFailed = remoteReels == null

            // إسقاط الاستجابات المتقادمة
            if (gen != requestGeneration.get()) {
                if (isManualRetry) isRetrying = false
                return@launch
            }

            val effectiveReels = if (!remoteReels.isNullOrEmpty()) {
                val sanitized = remoteReels.mapNotNull { ReelSafetyGuardrails.validateAndSanitizeReel(it) }
                fun isRemoteMockReel(r: SupabaseServices.ReelRow): Boolean {
                    val id = r.id.lowercase()
                    return id.startsWith("mock_") || id.startsWith("demo_") || id.startsWith("test_")
                }
                val filtered = sanitized.filterNot { isRemoteMockReel(it) }
                if (filtered.isNotEmpty()) {
                    isOfflineMode = false
                    isAlAtharMode = false
                    ReelPreferencesManager.saveCachedReels(context, filtered)
                    filtered
                } else {
                    val cached = ReelPreferencesManager.getCachedReels(context)
                    if (cached.isNotEmpty()) {
                        isOfflineMode = true
                        cached
                    } else {
                        isOfflineMode = false
                        AUTHENTIC_STARTER_REELS
                    }
                }
            } else if (fetchFailed) {
                val silentReels = SilentReelStorageManager.getDownloadedReels(context)
                if (silentReels.isNotEmpty()) {
                    isOfflineMode = true
                    isAlAtharMode = true
                    silentReels
                } else {
                    isOfflineMode = false
                    isAlAtharMode = false
                    emptyList()
                }
            } else {
                val silentReels = SilentReelStorageManager.getDownloadedReels(context)
                if (silentReels.isNotEmpty()) {
                    isOfflineMode = true
                    isAlAtharMode = true
                    silentReels
                } else {
                    val cached = ReelPreferencesManager.getCachedReels(context)
                    if (cached.isNotEmpty()) {
                        isOfflineMode = true
                        isAlAtharMode = false
                        cached
                    } else {
                        isOfflineMode = false
                    isAlAtharMode = false
                    selectedTab = 0
                        AUTHENTIC_STARTER_REELS
                    }
                }
            }

            loadingProgress = 0.88f
            allReels = effectiveReels
            canLoadMore = (remoteReels?.size ?: 0) >= REELS_PAGE_SIZE
            allReels.forEach { r ->
                dynamicLikeCounts[r.id] = r.likesCount
                likedReelIds[r.id] = likedReels.any { it.reelId == r.id }
            }
            loadingProgress = 0.98f
            lastWatchedReelId = null
            watchSeconds.clear()
            delay(120)
            loadingProgress = 1.0f
            delay(100)
            isLoading = false
            if (isManualRetry) isRetrying = false
            loadError = fetchFailed && allReels.isEmpty()

            val authId = SupabaseServices.Auth.getCurrentUserId()
            if (authId != null) {
                runCatching {
                    withTimeoutOrNull(5000L) {
                        SupabaseServices.Profiles.getBookmarkedReelIds(authId)
                    }
                }.getOrNull()?.let { ids ->
                    if (ids.isNotEmpty()) cloudBookmarks = ids
                }
            }
        }
    }

    /** تحميل الصفحة التالية من الخلاصة (تمرير لانهائي) */
    fun loadMoreReels() {
        if (isLoadingMore || !canLoadMore || allReels.isEmpty()) return
        isLoadingMore = true
        scope.launch {
            val gen = requestGeneration.incrementAndGet()
            val nextPage = runCatching {
                withTimeoutOrNull(7000L) {
                    SupabaseServices.Profiles.getActiveReels(limit = REELS_PAGE_SIZE, offset = remoteOffset)
                } ?: null
            }.onSuccess { ReelSafetyGuardrails.recordNetworkSuccess() }
                .onFailure { ReelSafetyGuardrails.recordNetworkFailure() }
                .getOrNull()
            isLoadingMore = false

            // إسقاط الاستجابات المتقادمة (طلب أحدث أثناء جلب هذه)
            if (gen != requestGeneration.get()) return@launch
            if (nextPage == null) return@launch // فشل صامت للتحميل الزائد: القائمة الحالية تبقى

            // تقدم الإزاحة بعدد الصفوف الخام المجلبة فعلاً من الخادم
            remoteOffset += nextPage.size

            val newOnes = nextPage.mapNotNull { ReelSafetyGuardrails.validateAndSanitizeReel(it) }
                .filter { r -> allReels.none { it.id == r.id } }
            canLoadMore = nextPage.size >= REELS_PAGE_SIZE
            if (newOnes.isNotEmpty()) {
                val updatedList = allReels + newOnes
                allReels = updatedList
                // تحديث الكاش المحلي بالقائمة الموسعة
                ReelPreferencesManager.saveCachedReels(context, updatedList)
                newOnes.forEach { r ->
                    if (!dynamicLikeCounts.containsKey(r.id)) {
                        dynamicLikeCounts[r.id] = r.likesCount
                        likedReelIds[r.id] = likedReels.any { it.reelId == r.id }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadReels()
    }

    // مزامنة قائمة الموثّقين عالمياً لعرض الشارة بشكل صحيح
    LaunchedEffect(Unit) {
        runCatching { VerificationManager.refreshFromCloud(context) }
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
                when (selectedTab) {
                    1 -> {
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
                    }
                    2 -> {
                        // Al-Athar tab: show silent seeds or offline-injected reels
                        reel.isSilentSeed || reel.publishType == "injection" || reel.tags.contains("أثر")
                    }
                    else -> true // For You tab
                }
            }
    }

    val pagerState = rememberPagerState(pageCount = { displayReels.size })

    // حالة التشغيل الفعلية للصفحة الحالية (مستمدة من خريطة الصفحات — لا تسرب حالة)
    val currentReelActuallyPlaying = pagePlayingStates[pagerState.currentPage] == true

    // إثبات المشاهدة عند مغادرة المقطع: تسجيل بالمدة الفعلية المقيسة (لا قيمة مختلقة)
    LaunchedEffect(pagerState.currentPage, displayReels) {
        if (displayReels.isEmpty()) return@LaunchedEffect
        val page = pagerState.currentPage
        if (page >= displayReels.size) return@LaunchedEffect
        if (lastWatchedReelId != null && lastWatchedReelId != displayReels[page].id) {
            finalizeWatch(lastWatchedReelId)
        }
        lastWatchedReelId = displayReels[page].id
    }

    // عدّاد المشاهدة: يتراكم فقط أثناء التشغيل الفعلي للمقطع الحالي (لا أثناء الإيقاف/الجلب)
    LaunchedEffect(currentReelActuallyPlaying, pagerState.currentPage, displayReels) {
        if (!currentReelActuallyPlaying || displayReels.isEmpty()) return@LaunchedEffect
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            delay(1000)
            val page = pagerState.currentPage
            if (page >= displayReels.size) break
            val id = displayReels[page].id
            watchSeconds[id] = (watchSeconds[id] ?: 0) + 1
        }
    }

    // عند مغادرة الشاشة نهائياً: إثبات مشاهدة آخر مقطع
    DisposableEffect(Unit) {
        onDispose {
            finalizeWatch(lastWatchedReelId)
        }
    }

    // تمرير لانهائي: جلب الصفحة التالية عند الاقتراب من نهاية القائمة الحالية
    LaunchedEffect(pagerState.currentPage, displayReels.size, isLoading) {
        if (isLoading || displayReels.isEmpty()) return@LaunchedEffect
        if (pagerState.currentPage >= displayReels.size - 3) {
            loadMoreReels()
        }
    }

    ReelCrashBoundary(onSafeExit = onBack) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // Full bleed without empty top gap
            bottomBar = {
                if (chromeVisible) {
                    bottomBar()
                }
            },
            containerColor = Color.Black
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // 🛠️ HUD المزامنة الصامتة للمطور
                SilentSyncDevFloatingHud(isDeveloper = isDeveloper)

                if (isLoading) {
                    ReelsInteractiveLoadingView(
                        progress = loadingProgress,
                        onRetry = { loadReels() }
                    )
                } else if (loadError && allReels.isEmpty()) {
                    // فشل التحميل الصريح: كارت تشخيص تفصيلي مع زر المثلث التفاعلي وسرعة الاتصال
                    ReelsFailureDiagnosticScreen(
                        diagnostic = lastDiagnostic,
                        isRetrying = isRetrying,
                        onRetry = { loadReels(isManualRetry = true) }
                    )
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
                                    onClick = { checkGuestInteraction { showPublishDialog = true } },
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
                if (displayReels.isEmpty()) {
                    // توجد مقاطع لكن لا نتائج مطابقة للفلاتر/البحث الحالية
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(46.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لا نتائج مطابقة لبحثك أو تصنيفك الحالي",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    selectedCategory = "ALL"
                                    searchQuery = ""
                                    isSearchActive = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.Default.FilterAltOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مسح الفلاتر", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
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
                        // محفوظ محلياً أو في المحفوظات السحابية (مزامنة عبر الأجهزة)
                        val isSaved = savedReels.any { it.reelId == reel.id } || cloudBookmarks.contains(reel.id)
                        val currentLikes = dynamicLikeCounts[reel.id] ?: reel.likesCount

                        val handleToggleLike: () -> Unit = {
                            checkGuestInteraction {
                                if (ReelSafetyGuardrails.canPerformLike(reel.id) && likeInFlight[reel.id] != true) {
                                    val newLikedState = !isLiked
                                    likeInFlight[reel.id] = true
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
                                        val authId = SupabaseServices.Auth.getCurrentUserId()
                                        
                                        if (authId == null) {
                                            // تراجع فوري: لا يمكن الإعجاب سحابياً بدون جلسة Supabase نشطة (RLS Requirement)
                                            likedReelIds[reel.id] = !newLikedState
                                            dynamicLikeCounts[reel.id] = if (newLikedState) currentLikes else currentLikes + 1
                                            ReelPreferencesManager.toggleLiked(
                                                context = context,
                                                reelId = reel.id,
                                                title = reel.title,
                                                userName = reel.userName,
                                                userAvatar = reel.userAvatar,
                                                videoUrl = reel.videoUrl,
                                                category = selectedCategory
                                            )
                                            Toast.makeText(context, "انتهت جلسة السحابة — يرجى تسجيل الدخول مجدداً", Toast.LENGTH_SHORT).show()
                                            likeInFlight[reel.id] = false
                                            return@launch
                                        }

                                        val ok = runCatching {
                                            withTimeoutOrNull(5000L) {
                                                if (newLikedState) {
                                                    SupabaseServices.Profiles.likeReel(reel.id, authId)
                                                } else {
                                                    SupabaseServices.Profiles.unlikeReel(reel.id, authId)
                                                }
                                            } ?: false
                                        }.getOrDefault(false)

                                        if (authId != null && !ok) {
                                            // فشل التسجيل السحابي: تراجع فوري عن التحديث المتفائل
                                            likedReelIds[reel.id] = !newLikedState
                                            dynamicLikeCounts[reel.id] = if (newLikedState) currentLikes else currentLikes + 1
                                            ReelPreferencesManager.toggleLiked(
                                                context = context,
                                                reelId = reel.id,
                                                title = reel.title,
                                                userName = reel.userName,
                                                userAvatar = reel.userAvatar,
                                                videoUrl = reel.videoUrl,
                                                category = selectedCategory
                                            )
                                            val errorMsg = if (!SupabaseServices.isSupabaseAvailable) {
                                                "الخدمات السحابية غير متوفرة حالياً"
                                            } else {
                                                "تعذر تحديث الإعجاب سحابياً — راجع اتصالك أو صلاحياتك"
                                            }
                                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                        }
                                        likeInFlight[reel.id] = false
                                    }
                                }
                            }
                        }

                        val handleToggleSave: () -> Unit = {
                            checkGuestInteraction {
                                val wasCloudSaved = cloudBookmarks.contains(reel.id)
                                val wasLocalSaved = savedReels.any { it.reelId == reel.id }
                                val nowLocalSaved = ReelPreferencesManager.toggleSave(
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
                                    if (nowLocalSaved) "تم حفظ المقطع في المحفوظات 🔖" else "تمت إزالة المقطع من المحفوظات",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // مزامنة سحابية للمستخدم المصادق — مع تراجع فوري عند الفشل
                                val authId = FirebaseAuthRepository.getEffectiveUserId(context) ?: SupabaseServices.Auth.getCurrentUserId()
                                if (authId != null) {
                                    // الحالة السحابية تحاكي الحالة المحلية النهائية
                                    val nowCloudSaved = nowLocalSaved
                                    if (nowCloudSaved) cloudBookmarks = cloudBookmarks + reel.id else cloudBookmarks = cloudBookmarks - reel.id
                                    scope.launch {
                                        val ok = runCatching {
                                            withTimeoutOrNull(5000L) {
                                                if (nowCloudSaved) {
                                                    SupabaseServices.Profiles.addReelBookmark(reel.id, authId)
                                                } else {
                                                    SupabaseServices.Profiles.removeReelBookmark(reel.id, authId)
                                                }
                                            } ?: false
                                        }.getOrDefault(false)
                                        if (!ok) {
                                            // تراجع سحابي + تراجع محلي (اتساق كامل بين الطبقتين)
                                            cloudBookmarks = if (wasCloudSaved) cloudBookmarks + reel.id else cloudBookmarks - reel.id
                                            if (nowLocalSaved != wasLocalSaved) {
                                                ReelPreferencesManager.toggleSave(
                                                    context = context,
                                                    reelId = reel.id,
                                                    title = reel.title,
                                                    userName = reel.userName,
                                                    userAvatar = reel.userAvatar,
                                                    videoUrl = reel.videoUrl,
                                                    category = selectedCategory
                                                )
                                            }
                                            Toast.makeText(context, "تعذر مزامنة المحفوظات مع السحابة", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }

                        val isNetworkPermitted = remember(autoplayWifiOnly) {
                            ReelSafetyGuardrails.isNetworkPermittedForAutoplay(context, autoplayWifiOnly)
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            val aspectRatio = remember(reel.metadata) {
                                reel.metadata?.get("aspect_ratio")?.toString()?.trim('"') ?: "9:16"
                            }
                            
                            ReelPlayer(
                                videoUrl = reel.videoUrl,
                                // بوابة التشغيل الموحّدة: الصفحة مرئية + الشبكة مسموحة + الشاشة نشطة
                                isPlaying = isPageVisible && isNetworkPermitted && isScreenActive,
                                aspectRatio = aspectRatio,
                                playbackSpeed = playbackSpeed,
                                volume = audioVolume,
                                brightness = screenBrightness,
                                onBrightnessChange = { screenBrightness = it },
                                onVolumeChange = { audioVolume = it },
                                onVideoEnded = if (!loopCurrentReel && autoScrollOnFinish && page < displayReels.size - 1) {
                                    {
                                        scope.launch {
                                            pagerState.animateScrollToPage(page + 1)
                                        }
                                    }
                                } else null,
                                onDoubleTapLike = {
                                    if (isGuest) {
                                        showGuestLoginPrompt = true
                                    } else {
                                        when (doubleTapAction) {
                                            "SAVE" -> handleToggleSave()
                                            else -> if (!isLiked) handleToggleLike()
                                        }
                                    }
                                },
                                onSingleTap = { chromeVisible = !chromeVisible },
                                // قياس التشغيل الفعلي لكل صفحة (لعدّاد مدة المشاهدة)
                                onActualPlayingChanged = { playing ->
                                    pagePlayingStates[page] = playing
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
                                commentsCount = reel.commentsCount + (commentBumps[reel.id] ?: 0),
                                isKhushuMode = isKhushuMode,
                                onToggleKhushu = { isKhushuMode = !isKhushuMode },
                                isGuest = isGuest,
                                onRequireLogin = { showGuestLoginPrompt = true },
                                onLike = handleToggleLike,
                                onSave = handleToggleSave,
                                onComment = {
                                    checkGuestInteraction { activeCommentReel = reel }
                                },
                                onAudioClick = {
                                    activeAudioReel = reel
                                },
                                onOpenDetails = {
                                    activeDetailsReel = reel
                                },
                                activeDetailsReel = activeDetailsReel,
                                onShare = {
                                    checkGuestInteraction {
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
                                    }
                                },
                                onMoreOptions = {
                                    checkGuestInteraction { activeMoreOptionsReel = reel }
                                },
                                onOpenCreatorProfile = { cId: String, cName: String, cAvatar: String? ->
                                    activeCreatorProfile = Triple(cId, cName, cAvatar)
                                }
                            )
                        }
                    }
                }

                // «مرصد قبس الهندسي» — collapsible geometric dock with all screen tools
                if (!isKhushuMode) {
                    GeometricToolsDock(
                        expanded = dockExpanded,
                        onToggle = {
                            dockExpanded = !dockExpanded
                            ReelPreferencesManager.triggerHaptic(context)
                        },
                        tools = listOf(
                            DockTool(
                                id = "publish",
                                icon = if (isDeveloper) Icons.Default.Verified else Icons.Default.Add,
                                label = if (isDeveloper) "نشر 👑" else "نشر",
                                onClick = {
                                    checkGuestInteraction {
                                        showPublishDialog = true
                                        ReelPreferencesManager.triggerHaptic(context)
                                    }
                                }
                            ),
                            DockTool(
                                id = "search",
                                icon = Icons.Default.Search,
                                label = "بحث",
                                onClick = {
                                    isSearchActive = !isSearchActive
                                    ReelPreferencesManager.triggerHaptic(context)
                                },
                                active = isSearchActive
                            ),
                            DockTool(
                                id = "sound",
                                icon = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                label = "الصوت",
                                onClick = {
                                    audioVolume = if (audioVolume <= 0.01f) 1f else 0f
                                    ReelPreferencesManager.triggerHaptic(context)
                                },
                                active = isAudioMuted,
                                accent = Color(0xFFEF4444)
                            ),
                            DockTool(
                                id = "loop",
                                icon = Icons.Default.Repeat,
                                label = "تكرار",
                                onClick = {
                                    loopCurrentReel = !loopCurrentReel
                                    ReelPreferencesManager.triggerHaptic(context)
                                },
                                active = loopCurrentReel
                            ),
                            DockTool(
                                id = "settings",
                                icon = Icons.Default.Tune,
                                label = "الإعدادات",
                                onClick = {
                                    showSettingsSheet = true
                                    ReelPreferencesManager.triggerHaptic(context)
                                }
                            ),
                            DockTool(
                                id = "history",
                                icon = Icons.Default.History,
                                label = "السجل",
                                onClick = {
                                    showHistorySheet = true
                                    ReelPreferencesManager.triggerHaptic(context)
                                }
                            ),
                            DockTool(
                                id = "refresh",
                                icon = Icons.Default.Refresh,
                                label = "تحديث",
                                onClick = {
                                    loadReels()
                                    ReelPreferencesManager.triggerHaptic(context)
                                }
                            )
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }

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

                             // Center Cybernetic Tabs Capsule (لك | متابعون | الأثر) — neatly fitted without truncation
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
                                                isAlAtharMode = false
                                                ReelPreferencesManager.triggerHaptic(context)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "لك ✦",
                                            fontFamily = CairoFont,
                                            fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            softWrap = false,
                                            maxLines = 1,
                                            color = if (selectedTab == 0) GoldPrimary else Color.White.copy(alpha = 0.7f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(2.dp))

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
                                                checkGuestInteraction {
                                                    selectedTab = 1
                                                    isAlAtharMode = false
                                                    ReelPreferencesManager.triggerHaptic(context)
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "متابعون",
                                            fontFamily = CairoFont,
                                            fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            softWrap = false,
                                            maxLines = 1,
                                            color = if (selectedTab == 1) GoldPrimary else Color.White.copy(alpha = 0.7f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(2.dp))

                                    // Al-Athar Mode Tab (Always visible for manual switching)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (selectedTab == 2) GoldPrimary.copy(alpha = 0.22f) else Color.Transparent)
                                            .border(
                                                BorderStroke(1.dp, if (selectedTab == 2) GoldPrimary.copy(alpha = 0.6f) else Color.Transparent),
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                if (!alAtharEnabled) {
                                                    showAlAtharExplanation = true
                                                } else {
                                                    selectedTab = 2
                                                    isAlAtharMode = true
                                                }
                                                ReelPreferencesManager.triggerHaptic(context)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "الأثر ✦",
                                            fontFamily = CairoFont,
                                            fontWeight = if (selectedTab == 2) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            softWrap = false,
                                            maxLines = 1,
                                            color = if (selectedTab == 2) GoldPrimary else Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            // Spacer mirroring the back button so the tabs stay perfectly centered
                            Spacer(modifier = Modifier.size(38.dp))
                        }

                        // 📡 شريط تنبيه هادئ عند تصفح الكاش المحلي بدون إنترنت (وضع غير متصل)
                        AnimatedVisibility(
                            visible = isOfflineMode,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF0F172A).copy(alpha = 0.90f),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
                                    modifier = Modifier.clickable {
                                        loadReels()
                                        ReelPreferencesManager.triggerHaptic(context)
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (isAlAtharMode) "« وضع الأثر ✦ نفحات من وحي السكينة دون اتصال »" else "📡 أنت تتصفح المحفوظات المخزنة محلياً (وضع غير متصل)",
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp,
                                            color = GoldPrimary,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "إعادة الاتصال",
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
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
            onCommentPosted = {
                commentBumps = commentBumps + (reel.id to (commentBumps[reel.id] ?: 0) + 1)
            },
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

    // Publish Reel Screen
    if (showPublishDialog) {
        ReelPublishScreen(
            currentUserId = currentSupabaseId ?: "anonymous",
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
            isGuest = isGuest,
            onRequireLogin = {
                activeCreatorProfile = null
                showGuestLoginPrompt = true
            },
            onOpenMyProfile = {
                activeCreatorProfile = null
                onOpenProfile()
            },
            onFollowChanged = { followVersion++ }
        )
    }

    // Video Details Bottom Sheet
    activeDetailsReel?.let { reel ->
        ReelDetailsBottomSheet(
            reel = reel,
            onOpenCreatorProfile = { id, name, avatar ->
                activeDetailsReel = null
                activeCreatorProfile = Triple(id, name, avatar)
            },
            onDismiss = { activeDetailsReel = null }
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

    // 🛡️ Guest Interaction Dialog
    if (showGuestLoginPrompt) {
        GuestInteractionDialog(
            onDismiss = { showGuestLoginPrompt = false },
            onLogin = {
                showGuestLoginPrompt = false
                onOpenProfile()
            }
        )
    }

    // 🕊️ Al-Athar Explanation Dialog
    if (showAlAtharExplanation) {
        AlAtharExplanationDialog(
            onDismiss = { showAlAtharExplanation = false },
            onEnable = {
                scope.launch {
                    ReelPreferencesManager.setAlAtharEnabled(context, true)
                }
                showAlAtharExplanation = false
            }
        )
    }
    }
} // end ReelsScreen

/**
 * 🛡️ GuestInteractionDialog — نافذة طلب تسجيل الدخول عند محاولة التفاعل كضيف
 */
@Composable
fun GuestInteractionDialog(
    onDismiss: () -> Unit,
    onLogin: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.95f))
                .border(BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.45f)), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = GoldPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "قفل تسجيل الدخول",
                            tint = GoldPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "تسجيل الدخول مطلوب للتفاعل ✨",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "مرحباً بك في قبس! لتتمكن من الإعجاب بالمقاطع، مشاركة التعليقات، حفظ الفيديوهات، ومتابعة القراء والعلماء، يرجى تسجيل الدخول بحسابك.",
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepSlate)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "تسجيل الدخول بحسابك",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(
                            "إلغاء / متابعة كضيف",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
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
    // العدّ الفعلي بعد التعليقات المُضافة جالسياً (الافتراضي يأخذ عدّاد الصف)
    commentsCount: Int = reel.commentsCount,
    isGuest: Boolean = false,
    onRequireLogin: () -> Unit = {},
    onLike: () -> Unit,
    onSave: () -> Unit,
    onComment: () -> Unit,
    onAudioClick: () -> Unit,
    onOpenDetails: () -> Unit,
    activeDetailsReel: SupabaseServices.ReelRow?,
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

    // أدوات التفاعل ظاهرة افتراضياً (بارتي تيك توك) — وتُطوى بسهم «التفاعل» عند الرغبة
    var actionsExpanded by remember { mutableStateOf(true) }
    // بطاقة الناشر والوصف مطوية افتراضياً، وتُفتح بسهم «التفاصيل»
    var infoExpanded by remember { mutableStateOf(false) }

    // معلومات الناشر: حالة المتابعة وما إذا كان المقطع للمستخدم نفسه
    val hudContext = LocalContext.current
    val hudViewer = remember { CreatorFollowManager.currentViewer(hudContext) }
    val creatorVerified = remember(reel.userId) { VerificationManager.isVerified(hudContext, reel.userId) }
    var isFollowingCreator by remember(reel.userId) {
        mutableStateOf(CreatorFollowManager.isFollowing(hudContext, reel.userId))
    }
    val isOwnReel = remember(reel.userId) {
        CreatorFollowManager.isSameAccount(
            reel.userId, reel.userName,
            hudViewer.id, hudViewer.displayName, hudViewer.email
        )
    }

    if (isKhushuMode) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Bottom Meta Info (audio badge + collapsible creator/description)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 66.dp, bottom = 96.dp)
                .fillMaxWidth()
        ) {
            // Details are now shown in ReelDetailsBottomSheet triggered by the Details arrow button
            // This block is removed from the HUD overlay to prevent redundancy and clutter.
            Spacer(modifier = Modifier.height(10.dp))

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
                        text = "الصوت الأصلي للمقطع",
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = CairoFont,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Bottom-left vertical geometric action rail (collapsible)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 84.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = actionsExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // مُرتّبة حسب الأهمية: إعجاب ← تعليق ← حفظ ← مشاركة ← المزيد
                    ReelRailButton(
                        icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = formatCount(likesCount),
                        tint = if (isLiked) Color(0xFFEF4444) else Color.White,
                        onClick = onLike
                    )
                    ReelRailButton(
                        icon = Icons.Default.Comment,
                        label = formatCount(commentsCount),
                        tint = Color.White,
                        onClick = onComment
                    )
                    ReelRailButton(
                        icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        label = if (isSaved) "محفوظ" else "حفظ",
                        tint = if (isSaved) GoldPrimary else Color.White,
                        onClick = onSave
                    )
                    ReelRailButton(
                        icon = Icons.Default.Share,
                        label = formatCount(reel.sharesCount),
                        tint = Color.White,
                        onClick = onShare
                    )
                    ReelRailButton(
                        icon = Icons.Default.MoreHoriz,
                        label = "المزيد",
                        tint = Color.White.copy(alpha = 0.85f),
                        onClick = onMoreOptions
                    )

                    // 🎵 Spinning audio disc (geometric hub)
                    Box(
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                            .border(1.5.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape)
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

        // الأسهم الثلاثة بنفس القياس وبنفس الارتفاع مع اسم كل سهم أسفله
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ReelArrowButton(
                expanded = activeDetailsReel != null,
                label = "التفاصيل",
                onClick = onOpenDetails
            )
            ReelArrowButton(
                expanded = actionsExpanded,
                label = "التفاعل",
                onClick = { actionsExpanded = !actionsExpanded }
            )
        }
    }
}

/**
 * سهم تبديل موحّد (نفس القياس) مع اسمه أسفله
 */
@Composable
fun ReelArrowButton(
    expanded: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(46.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.75f))
                .border(1.4.dp, GoldPrimary.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = label,
                tint = GoldPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            color = GoldPrimary,
            fontFamily = CairoFont,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
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

/**
 * زر تفاعل هندسي بحواف مربعة دائرية — يُستخدم في عمود التفاعل اليساري
 */
@Composable
fun ReelRailButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(46.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.62f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(13.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontFamily = CairoFont,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsBottomSheet(
    reel: SupabaseServices.ReelRow,
    currentUserId: String,
    onCommentPosted: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var commentText by remember { mutableStateOf("") }
    var commentsList by remember { mutableStateOf<List<SupabaseServices.ReelCommentRow>>(emptyList()) }
    var isPosting by remember { mutableStateOf(false) }
    // حالة جلب التعليقات: تحميل / فشل — يُميَّزان عن القائمة الفارغة
    var commentsLoading by remember { mutableStateOf(true) }
    var commentsError by remember { mutableStateOf(false) }

    LaunchedEffect(reel.id) {
        commentsLoading = true
        commentsError = false
        val loaded = runCatching {
            SupabaseServices.Profiles.getReelComments(reel.id)
        }.getOrDefault(null)
        if (loaded == null) {
            commentsError = true
        } else {
            commentsList = loaded
        }
        commentsLoading = false
    }

    val submitComment: (String) -> Unit = submit@{ raw ->
        val sanitized = ReelSafetyGuardrails.sanitizeCommentText(raw) ?: return@submit
        if (isPosting) return@submit
        if (!ReelSafetyGuardrails.canPostComment()) {
            Toast.makeText(context, "يرجى الانتظار قليلاً قبل إرسال تعليق آخر ⏱️", Toast.LENGTH_SHORT).show()
            return@submit
        }
        isPosting = true
        coroutineScope.launch {
            // يُدرج الصف الفعلي الذي أعادته الخلفية (بمعرّفه ووقته الحقيقيين) — لا صف مختلق محلياً
            val inserted = runCatching {
                withTimeoutOrNull(6000L) {
                    SupabaseServices.Profiles.addReelComment(reel.id, currentUserId, sanitized)
                }
            }.getOrNull()
            if (inserted != null) {
                commentsList = commentsList + inserted
                commentText = ""
                onCommentPosted()
            } else {
                Toast.makeText(context, "تعذر إرسال التعليق حالياً، يرجى المحاولة لاحقاً", Toast.LENGTH_SHORT).show()
            }
            isPosting = false
        }
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

            if (commentsLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            } else if (commentsError) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "تعذر تحميل التعليقات",
                            color = Color.White.copy(alpha = 0.6f),
                            fontFamily = CairoFont,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (commentsList.isEmpty()) {
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

            // تفاعلات سريعة تُنشر كتعليق بدل ازدحام الشاشة
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(SPIRITUAL_REACTIONS.take(4)) { reaction ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = GoldPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !isPosting) {
                                submitComment("${reaction.icon} ${reaction.text}")
                            }
                    ) {
                        Text(
                            text = "${reaction.icon} ${reaction.text}",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .imePadding(),
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
                    onClick = { submitComment(commentText) },
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


/**
 * 🌌 شاشة التحميل الإلكترونية الحديثة بنظام HUD الرقمي
 * متصلة بالنسبة المئوية الفعلية للتحميل مع تصميم كوانتي نيون أنيق.
 */
@Composable
fun ReelsInteractiveLoadingView(
    progress: Float,
    onRetry: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "electronicProgressAnim"
    )

    val percentInt = (animatedProgress * 100).toInt()

    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val scannerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "scannerAngle"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse"
    )

    var waitTimerSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            waitTimerSeconds++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // ── 1. دائرة الـ HUD الإلكترونية المتصلة بالنسبة المئوية الحقيقية ──
            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center
            ) {
                // هالة ضوئية خلفية نيون
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(glowPulse)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF00E5FF).copy(alpha = 0.22f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )

                // مسار الحلقة الخلفية
                Canvas(modifier = Modifier.size(120.dp)) {
                    drawCircle(
                        color = Color(0xFF1E293B).copy(alpha = 0.5f),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }

                // قوس النسبة المئوية الفعلي (Progress Arc)
                Canvas(modifier = Modifier.size(120.dp)) {
                    val sweep = animatedProgress * 360f
                    if (sweep > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00E5FF),
                                    GoldPrimary,
                                    Color(0xFF10B981),
                                    Color(0xFF00E5FF)
                                )
                            ),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(
                                width = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                // شعاع المسح الإلكتروني
                Canvas(
                    modifier = Modifier
                        .size(120.dp)
                        .rotate(scannerAngle)
                ) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFF00E5FF).copy(alpha = 0.35f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 45f,
                        useCenter = true
                    )
                }

                // النسبة المئوية في المنتصف بأسلوب رقمي إلكتروني حديث
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$percentInt%",
                        color = Color(0xFF00E5FF),
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "LIVE // SYNC",
                        color = GoldPrimary.copy(alpha = 0.85f),
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 2. العبارة النصية المطلوبة بدقة: جاري التحميل... ──
            Text(
                text = "جاري التحميل...",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── 3. شريط إلكتروني أفقي يعكس النسبة بدقة ──
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00E5FF), GoldPrimary)
                            )
                        )
                )
            }

            // زر إعادة المحاولة في حال تأخر الاتصال
            if (waitTimerSeconds >= 5) {
                Spacer(modifier = Modifier.height(18.dp))
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E5FF))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "إعادة المحاولة 🔄",
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
 * شاشة التشخيص الشامل عند فشل جلب مقاطع الريلز من السحابة.
 * تقدم تشخيصاً فنياً دقيقاً ومباشراً بدون إخفاء التفاصيل أو فشل صامت.
 */
@Composable
fun ReelsFailureDiagnosticScreen(
    diagnostic: SupabaseServices.ReelsFetchDiagnosticResult?,
    isRetrying: Boolean,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF090D16), Color(0xFF05070B), Color(0xFF070B14))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // كارت التشخيص الشامل
            ReelsDiagnosticCard(
                diagnostic = diagnostic,
                isRetrying = isRetrying
            )

            Spacer(modifier = Modifier.height(22.dp))

            // الزر التفاعلي للمثلث اللانهائي مع عرض سرعة الاتصال
            InfiniteTriangleRetryButton(
                latencyMs = diagnostic?.latencyMs,
                isRetrying = isRetrying,
                onRetry = onRetry
            )
        }
    }
}

/**
 * كارت تشخيص تفصيلي يعرض سبب الخطأ ورمز الاستجابة وحالة السحابة وقاطع الدائرة.
 */
@Composable
fun ReelsDiagnosticCard(
    diagnostic: SupabaseServices.ReelsFetchDiagnosticResult?,
    isRetrying: Boolean
) {
    val httpCode = diagnostic?.httpCode
    val stage = diagnostic?.errorStage ?: "NETWORK"
    val latency = diagnostic?.latencyMs ?: 0L
    val isCircuit = diagnostic?.isCircuitBreakerActive == true
    val cooldownSec = diagnostic?.remainingCooldownSec ?: 0

    val statusColor = when {
        isRetrying -> Color(0xFF00E5FF)
        httpCode == 200 -> Color(0xFF4ADE80)
        isCircuit -> Color(0xFFF59E0B)
        httpCode == 401 || httpCode == 403 -> Color(0xFFEF4444)
        else -> Color(0xFFF87171)
    }

    val statusTitle = when {
        isRetrying -> "جاري الفحص المباشر للسحابة..."
        isCircuit -> "قاطع الدائرة نشط (حماية التردد)"
        httpCode == 401 || httpCode == 403 -> "خطأ في مصادقة مفتاح Supabase"
        stage == "TIMEOUT" -> "انتهت مهلة استجابة الخادم"
        stage == "CONFIG" -> "السحابة غير مهيأة"
        else -> "تعذر استلام مقاطع الخلاصة"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1424).copy(alpha = 0.95f)),
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.6f), GoldPrimary.copy(alpha = 0.3f)))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // ترويسة الكارت
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = "تشخيص السحابة المتقدم",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                }

                // شارة الحالة
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (httpCode != null && httpCode > 0) "HTTP $httpCode" else stage,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // شريط المؤشرات الثلاثية (HTTP Code, Ping Latency, Guardrail)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // مؤشر زمن الاتصال والسرعة
                MetricBadge(
                    modifier = Modifier.weight(1f),
                    label = "سرعة الاستجابة",
                    value = if (latency > 0) "${latency}ms" else "--",
                    subValue = when {
                        latency in 1..250 -> "سريع 🟢"
                        latency in 251..650 -> "متوسط 🟡"
                        latency > 650 -> "بطيء 🟠"
                        else -> "منقطع ⚪"
                    },
                    accentColor = when {
                        latency in 1..250 -> Color(0xFF4ADE80)
                        latency in 251..650 -> Color(0xFFFACC15)
                        else -> Color(0xFFFB923C)
                    }
                )

                // مؤشر رمز الاستجابة
                MetricBadge(
                    modifier = Modifier.weight(1f),
                    label = "رمز الخادم",
                    value = if (httpCode != null && httpCode > 0) "$httpCode" else "ERR",
                    subValue = when (httpCode) {
                        200 -> "ناجح"
                        401, 403 -> "مرفوض"
                        408 -> "تأخر"
                        429 -> "محدود"
                        null, 0 -> "لا اتصال"
                        else -> "استثناء"
                    },
                    accentColor = if (httpCode == 200) Color(0xFF4ADE80) else Color(0xFFF87171)
                )

                // مؤشر درع الأمان
                MetricBadge(
                    modifier = Modifier.weight(1f),
                    label = "قاطع الحماية",
                    value = if (isCircuit) "${cooldownSec}s" else "جاهز",
                    subValue = if (isCircuit) "مؤمّن 🛡️" else "طبيعي ⚡",
                    accentColor = if (isCircuit) Color(0xFFF59E0B) else Color(0xFF00E5FF)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // نص التوضيح والخطأ الدقيق
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = statusTitle,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = diagnostic?.errorMessage ?: "تحقق من اتصال جهازك بالإنترنت أو أعد المحاولة لتجاوز التهدئة.",
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subValue: String,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161F36),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontFamily = CairoFont,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subValue,
                fontFamily = CairoFont,
                fontSize = 9.5.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * زر تفاعلي ينشئ مثلثاً هندسياً مع حالة تفاعلية لانهائية ويعرض سرعة الاتصال بدقة.
 */
@Composable
fun InfiniteTriangleRetryButton(
    latencyMs: Long?,
    isRetrying: Boolean,
    onRetry: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "infinite_triangle_transition")

    // تدوير المثلث اللانهائي
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isRetrying) 1000 else 4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "triangle_rotation"
    )

    // نبض الإشعاع والهالة
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "triangle_pulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "triangle_glow"
    )

    Surface(
        onClick = onRetry,
        enabled = !isRetrying,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(
            1.5.dp,
            Brush.sweepGradient(
                listOf(
                    GoldPrimary.copy(alpha = glowAlpha),
                    Color(0xFF00E5FF).copy(alpha = glowAlpha),
                    GoldPrimary.copy(alpha = glowAlpha)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // المثلث الهندسي المتوهج التفاعلي
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(36.dp)
                        .rotate(rotation)
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = size.minDimension / 2.2f

                    // زوايا المثلث متساوي الأضلاع
                    val p1 = Offset(cx, cy - r)
                    val p2 = Offset(cx + (r * cos(PI / 6)).toFloat(), cy + (r * sin(PI / 6)).toFloat())
                    val p3 = Offset(cx - (r * cos(PI / 6)).toFloat(), cy + (r * sin(PI / 6)).toFloat())

                    val trianglePath = Path().apply {
                        moveTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }

                    // 1. هالة ضوئية خارجية
                    drawPath(
                        path = trianglePath,
                        color = Color(0xFF00E5FF).copy(alpha = glowAlpha * 0.3f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 2. تعبئة زجاجية ذهبية نيونية
                    drawPath(
                        path = trianglePath,
                        brush = Brush.linearGradient(
                            listOf(
                                GoldPrimary.copy(alpha = 0.35f),
                                Color(0xFF00E5FF).copy(alpha = 0.2f)
                            )
                        )
                    )

                    // 3. حواف المثلث النيونية
                    drawPath(
                        path = trianglePath,
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF00E5FF),
                                GoldPrimary,
                                Color(0xFF00E5FF)
                            )
                        ),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 4. نواة مركزية مضيئة
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 2.5.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // النصوص الإرشادية وحالة الزر
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isRetrying) "جاري تجاوز القاطع والفحص..." else "إعادة المحاولة وفحص الاتصال",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isRetrying) "تصفير قاطع الدائرة فورياً واستعلام الخادم" else "تصفير فوري للحماية واختبار سرعة الاستجابة",
                    fontFamily = CairoFont,
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // شارة سرعة الاتصال الفورية
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (latencyMs != null && latencyMs > 0) "${latencyMs}ms" else "فحص",
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF00E5FF)
                    )
                }
            }
        }
    }
}


/**
 * 🛰️ ReelDetailsBottomSheet — لوحة تفاصيل المقطع (العنوان، التصنيف، الوصف، والناشر)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelDetailsBottomSheet(
    reel: SupabaseServices.ReelRow,
    onOpenCreatorProfile: (String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hudViewer = remember { CreatorFollowManager.currentViewer(context) }
    val creatorVerified = remember(reel.userId) { VerificationManager.isVerified(context, reel.userId) }
    var isFollowingCreator by remember(reel.userId) {
        mutableStateOf(CreatorFollowManager.isFollowing(context, reel.userId))
    }
    val isOwnReel = remember(reel.userId) {
        CreatorFollowManager.isSameAccount(
            reel.userId, reel.userName,
            hudViewer.id, hudViewer.displayName, hudViewer.email
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = GoldPrimary.copy(alpha = 0.5f))
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: Creator Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = reel.userAvatar ?: "https://ui-avatars.com/api/?name=${reel.userName}&background=D4AF37&color=0B0F19",
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, GoldPrimary, CircleShape)
                        .clickable {
                            onOpenCreatorProfile(reel.userId, reel.userName ?: "صانع محتوى قبس", reel.userAvatar)
                        },
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reel.userName ?: "صانع محتوى قبس",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (creatorVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "موثق",
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "نشر ${formatRelativeTime(reel.createdAt)}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = CairoFont,
                        fontSize = 12.sp
                    )
                }
                
                if (!isOwnReel && reel.userId.isNotBlank()) {
                    Button(
                        onClick = {
                            val next = CreatorFollowManager.setFollowed(
                                context, hudViewer,
                                reel.userId, reel.userName ?: "",
                                reel.userAvatar, !isFollowingCreator
                            )
                            if (next != null) isFollowingCreator = next
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowingCreator) Color.White.copy(alpha = 0.1f) else GoldPrimary,
                            contentColor = if (isFollowingCreator) Color.White else DeepSlate
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = if (isFollowingCreator) "متابَع" else "متابعة",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = reel.title,
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tags / Category
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                reel.tags.filter { it.isNotBlank() }.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GoldPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = tag,
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            if (!reel.description.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = reel.description,
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = CairoFont,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Statistics / Interaction Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(icon = Icons.Default.Favorite, label = "${reel.likesCount} إعجاب")
                StatItem(icon = Icons.Default.Comment, label = "${reel.commentsCount} تعليق")
                StatItem(icon = Icons.Default.Visibility, label = "${reel.viewsCount} مشاهدة")
            }
        }
    }
}

@Composable
fun AlAtharExplanationDialog(
    onDismiss: () -> Unit,
    onEnable: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.98f))
                .border(BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.5f)), RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = GoldPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "ميزة وضع الأثر 🕊️",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "عند تفعيل هذه الميزة، سيقوم التطبيق بتحميل مجموعة من المقاطع الإيمانية المختارة في الخلفية، لتتمكن من مشاهدتها والانتفاع بها حتى في حال انقطاع اتصالك بالإنترنت.",
                    fontFamily = CairoFont,
                    fontSize = 13.5.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onEnable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepSlate)
                    ) {
                        Text(
                            "تفعيل الآن ✨",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "ليس الآن",
                            fontFamily = CairoFont,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = GoldPrimary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontFamily = CairoFont, fontSize = 12.sp)
    }
}
