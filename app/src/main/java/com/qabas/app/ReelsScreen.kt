@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.AmiriFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
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
/**
 * مقاطع احتياطية محلية تُعرض فقط عند غياب أي مقاطع من قاعدة البيانات.
 * لا تحمل هذه المقاطع أي بيانات تفاعل حقيقية، فعداداتها كلها صفر —
 * يُمنع عرض أرقام مخترعة؛ العدادات الحقيقية يجب أن تأتي من قاعدة البيانات.
 */
private val AUTHENTIC_STARTER_REELS: List<SupabaseServices.ReelRow> = listOf(
    SupabaseServices.ReelRow(
        id = "qabas_quran_recitation_starter",
        userId = "qabas_reciter_1",
        userName = "تلاوات خاشعة 🌿",
        userAvatar = "https://images.unsplash.com/photo-1544717305-2782549b5136?w=200&q=80",
        title = "﴿الَّذِينَ آمَنُوا وَتَطْمَئِنُّ قُلُوبُهُم بِذِكْرِ اللَّهِ﴾ 🌿 تلاوة خاشعة وسكينة",
        description = "تلاوة قرآنية مباركة تأخذ القلب إلى آفاق الطمأنينة والسكينة في رحاب كلام الله تبارك وتعالى.",
        // مستضاف في سحابة خادم المسلم نفسها — لا اعتماد على مزودي خارجيين يحجبون الروابط
        videoUrl = "https://egkgygbvoeevakwkbvfu.supabase.co/storage/v1/object/public/media-assets/starter_reels/quran_recitation.mp4",
        thumbnailUrl = "https://images.unsplash.com/photo-1609599006353-e629aaabfeae?w=600&q=80",
        tags = listOf("قرآن", "تلاوة", "خشوع", "سكينة", "ذكر"),
        likesCount = 0,
        commentsCount = 0,
        sharesCount = 0,
        createdAt = "2025-01-01T12:00:00Z"
    ),
    SupabaseServices.ReelRow(
        id = "qabas_faith_lecture_starter",
        userId = "qabas_reciter_2",
        userName = "مشكاة الحكمة والموعظة 📜",
        userAvatar = "https://images.unsplash.com/photo-1548013146-72479768bada?w=200&q=80",
        title = "هادم اللذات 📜 وقفة صادقة مع الآخرة وزوال الدنيا",
        description = "موعظة مؤثرة تدق أبواب القلوب: تذكير بزوال الدنيا وسرعة الرحيل إلى الله سبحانه وتعالى.",
        // مستضاف في سحابة خادم المسلم نفسها — لا اعتماد على مزودي خارجيين يحجبون الروابط
        videoUrl = "https://egkgygbvoeevakwkbvfu.supabase.co/storage/v1/object/public/media-assets/starter_reels/faith_lecture.mp4",
        thumbnailUrl = "https://images.unsplash.com/photo-1548013146-72479768bada?w=600&q=80",
        tags = listOf("مواعظ", "تذكير", "آخرة", "زهد", "قلب"),
        likesCount = 0,
        commentsCount = 0,
        sharesCount = 0,
        createdAt = "2025-01-02T15:30:00Z"
    )
)

/**
 * 🛰️ ReelsScreen — مشغل ومجتمع ريلز خادم المسلم الفضائي
 * النمط المعتمد: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 * تصميم متجاوب بدون فراغات علوية وبدون تكدس أو تداخل للعناصر
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    // روابط عميقة عند الدخول (الدفعة 5 — الجزء C؛ تُمرَّر من AppNavigation).
    initialReelId: String? = null,
    initialCreatorId: String? = null,
    initialCreatorName: String? = null,
    initialCreatorAvatar: String? = null
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
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
    val watchHistory by ReelPreferencesManager.watchHistory.collectAsState()
    val alAtharEnabled by ReelPreferencesManager.alAtharEnabled.collectAsState()

    var allReels by remember { mutableStateOf<List<SupabaseServices.ReelRow>>(emptyList()) }
    var atharReels by remember { mutableStateOf(SilentReelStorageManager.getDownloadedReels(context)) }
    val silentSyncState by SilentReelPrefetcher.syncState.collectAsState()
    LaunchedEffect(silentSyncState.downloadedCount, silentSyncState.lastMessage) {
        atharReels = SilentReelStorageManager.getDownloadedReels(context)
    }
    var hiddenReelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reportedReelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        hiddenReelIds = ReelPreferencesManager.getHiddenReelIds(context)
    }
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
    val activeReelLoadJob = remember { java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>(null) }

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
    var selectedTab by remember { mutableIntStateOf(0) } // 0: لك (For You), 1: متابعون (Following), 2: الأثر (Al-Athar), 3: الأحدث (Newest)
    var selectedCategory by remember { mutableStateOf("ALL") }
    var showExplore by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var showAlAtharExplanation by remember { mutableStateOf(false) }

    // Sheets State
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }
    var activeDetailsReel by remember { mutableStateOf<SupabaseServices.ReelRow?>(null) }
    var activeCommentReel by remember { mutableStateOf<SupabaseServices.ReelRow?>(null) }
    var activeMoreOptionsReel by remember { mutableStateOf<SupabaseServices.ReelRow?>(null) }
    var activeCreatorProfile by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var activeAudioReel by remember { mutableStateOf<SupabaseServices.ReelRow?>(null) }
    var followVersion by remember { mutableIntStateOf(0) }
    val viewer = remember(context, followVersion) { CreatorFollowManager.currentViewer(context) }

    // Interactive & Spatial Experience State
    var isKhushuMode by remember { mutableStateOf(false) }
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
    LaunchedEffect(alAtharEnabled) {
        if (!alAtharEnabled && selectedTab == 2 && UserPreferencesManager.networkStatus.value == ConnectivityObserver.Status.Available) {
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
    var resumeRefreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isScreenActive = true
                    // إعادة قراءة التفاعلات بعد العودة من الخلفية؛ قد تكون الجلسة
                    // استُعيدت أو اكتمل الطلب السحابي أثناء غياب الشاشة.
                    resumeRefreshTick++
                }
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

    // 📡 إعادة استخدام حالة الاتصال المركزية التي يملكها التطبيق؛ لا ينشئ قسم الريلز مراقباً ثانياً.
    val networkStatus by UserPreferencesManager.networkStatus.collectAsState()
    val networkStatusReady by UserPreferencesManager.networkStatusReady.collectAsState()
    val isOnline = networkStatusReady && networkStatus == ConnectivityObserver.Status.Available
    var wasEverOffline by remember { mutableStateOf(false) }

    // User details for comments & cloud syncing
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val currentUserName = prefs.getString("user_name", "صانع محتوى خادم المسلم") ?: "صانع محتوى خادم المسلم"
    val currentUserEmail = prefs.getString("user_email", "guest@qabas.studio") ?: "guest@qabas.studio"
    // All screens consume the repository's authenticated snapshot. This reads no session,
    // performs no profile request, and cannot turn a temporary error into a login redirect.
    val accountAuthState by SupabaseRepository.getInstance().authState.collectAsState()
    val authSnapshot = remember(accountAuthState) {
        accountAuthState.authenticatedSessionSnapshot()
    }
    val currentSupabaseId = authSnapshot?.userId
    // Never use an email or locally cached account ID as an interaction owner.
    val currentUserId = currentSupabaseId.orEmpty()

    // App-metadata role remains the only role source; the owner allowlist stays separate.
    val isDeveloper = authSnapshot?.let {
        SupabaseServices.Auth.isDeveloperRole(it.role) || CloudServices.isOwnerAccount(it.email)
    } == true
    val isGuest = accountAuthState.requiresInteractiveLogin()
    var showGuestLoginPrompt by remember { mutableStateOf(false) }
    var pendingInteractionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    suspend fun refreshLikedReelsFromCloud(userId: String? = null) {
        val authId = userId ?: authSnapshot?.userId
        if (authId.isNullOrBlank()) {
            if (accountAuthState !is AuthState.Authenticated) likedReelIds.clear()
            return
        }

        // A null result means the request failed; an empty set is authoritative and
        // clears stale local likes. No auth/session restoration is attempted here.
        repeat(3) { attempt ->
            val cloudLikedIds = withTimeoutOrNull(15_000L) {
                SupabaseServices.Profiles.getLikedReelIds(authId)
            }
            if (cloudLikedIds != null) {
                likedReelIds.clear()
                cloudLikedIds.forEach { likedReelIds[it] = true }
                return
            }
            if (attempt < 2) delay(750L)
        }
    }

    LaunchedEffect(accountAuthState, isOnline) {
        when (accountAuthState) {
            is AuthState.Authenticated -> {
                if (!isOnline) return@LaunchedEffect
                val userId = authSnapshot?.userId
                if (!userId.isNullOrBlank()) {
                    refreshLikedReelsFromCloud(userId)
                    withTimeoutOrNull(8_000L) {
                        SupabaseServices.Profiles.getBookmarkedReelIds(userId)?.let { cloudBookmarks = it }
                    }
                    val pending = pendingInteractionAction
                    if (pending != null) {
                        pendingInteractionAction = null
                        pending()
                        Toast.makeText(context, "تم توثيق الحساب واستئناف العملية تلقائياً ✨", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    likedReelIds.clear()
                    cloudBookmarks = emptySet()
                }
            }
            is AuthState.Guest, AuthState.Unauthenticated -> {
                // Clear account-owned UI only after a definitive sign-out/guest state.
                likedReelIds.clear()
                cloudBookmarks = emptySet()
                if (pendingInteractionAction != null) showGuestLoginPrompt = true
            }
            is AuthState.Loading, is AuthState.Error -> Unit
        }
    }

    LaunchedEffect(resumeRefreshTick, accountAuthState, isOnline) {
        if (isOnline && resumeRefreshTick > 0 && accountAuthState is AuthState.Authenticated) {
            refreshLikedReelsFromCloud()
        }
    }

    /**
     * 🛡️ Guest Interaction Gatekeeper
     * Intercepts actions for guests and shows login prompt with automatic resume.
     */
    fun checkGuestInteraction(action: () -> Unit) {
        when (accountAuthState) {
            is AuthState.Authenticated -> {
                if (authSnapshot != null) action()
                else Toast.makeText(context, "جارٍ تجهيز حالة الحساب. حاول بعد لحظة.", Toast.LENGTH_SHORT).show()
            }
            is AuthState.Guest, AuthState.Unauthenticated -> {
                pendingInteractionAction = action
                showGuestLoginPrompt = true
            }
            is AuthState.Loading -> {
                pendingInteractionAction = action
                Toast.makeText(context, "جارٍ تجهيز الحساب؛ لن تحتاج إلى تسجيل الدخول من جديد.", Toast.LENGTH_SHORT).show()
            }
            is AuthState.Error -> {
                Toast.makeText(context, "تعذر التحقق من حالة الحساب مؤقتاً. لم يتم تحويلك لتسجيل الدخول.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * إثبات المشاهدة: تسجيل سحابي حصري في Supabase دون أي زيادة محلية وهمية في الذاكرة.
     */
    fun finalizeWatch(reelId: String?) {
        val rid = reelId ?: return
        val secs = watchSeconds[rid] ?: 0
        if (secs <= 0) return

        // تم حذف النظام المحلي: لا زيادة وهمية للعداد محلياً ولا كتابة محلية
        if (isOnline && selectedTab != 2 && !isAlAtharMode && ReelSafetyGuardrails.isSupabaseCallsAllowed()) {
            scope.launch {
                val effectiveUser = currentSupabaseId ?: return@launch
                val cloudViews = SupabaseServices.Profiles.recordReelWatch(rid, effectiveUser, secs)
                    ?: return@launch
                // Refresh the visible value from the authoritative cloud result only.
                allReels = allReels.map { item ->
                    if (item.id == rid) item.copy(viewsCount = cloudViews) else item
                }
            }
        }
    }

    fun loadReels(isManualRetry: Boolean = false) {
        if (!networkStatusReady) return
        activeReelLoadJob.getAndSet(null)?.cancel()
        val loadJob = scope.launch {
            if (isManualRetry) isRetrying = true
            isLoading = true
            loadError = false
            loadingProgress = 0.15f
            val gen = requestGeneration.incrementAndGet()
            loadingProgress = 0.35f

            if (!isOnline) {
                atharReels = SilentReelStorageManager.getDownloadedReels(context)
                selectedTab = 2
                selectedCategory = "ALL"
                searchQuery = ""
                isSearchActive = false
                isAlAtharMode = true
                isOfflineMode = true
                canLoadMore = false
                loadingProgress = 1f
                isLoading = false
                loadError = false
                if (isManualRetry) isRetrying = false
                return@launch
            }

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
                    isOfflineMode = false
                    isAlAtharMode = selectedTab == 2
                    if (cached.isNotEmpty()) cached else AUTHENTIC_STARTER_REELS
                }
            } else if (fetchFailed) {
                val cached = ReelPreferencesManager.getCachedReels(context)
                // فشل الطلب لا يغيّر التبويب؛ وضع الأثر يُحدّد من حالة الاتصال المركزية فقط.
                isOfflineMode = false
                isAlAtharMode = selectedTab == 2
                cached
            } else {
                val cached = ReelPreferencesManager.getCachedReels(context)
                isOfflineMode = false
                isAlAtharMode = selectedTab == 2
                if (cached.isNotEmpty()) cached else AUTHENTIC_STARTER_REELS
            }

            loadingProgress = 0.88f
            allReels = effectiveReels
            // إصلاح (الدفعة 3): الإزاحة الفعلية بعد أول جلب؛ كانت تبقى 0 فكان
            // تحميل المزيد يعيد جلب الصفحة 0 نفسها.
            remoteOffset = remoteReels?.size ?: 0
            canLoadMore = (remoteReels?.size ?: 0) >= REELS_PAGE_SIZE
            loadingProgress = 0.98f
            lastWatchedReelId = null
            watchSeconds.clear()
            delay(120)
            loadingProgress = 1.0f
            delay(100)
            isLoading = false
            if (isManualRetry) isRetrying = false
            loadError = fetchFailed && allReels.isEmpty()

            // Account-owned likes/bookmarks load independently from the public reel feed;
            // AuthState's observer above handles them when a verified account is ready.
        }
        activeReelLoadJob.set(loadJob)
    }

    // لا يغيّر القسم ولا يبدأ طلباً سحابياً قبل تثبيت القراءة المركزية الأولى.
    LaunchedEffect(networkStatusReady, networkStatus) {
        if (!networkStatusReady) return@LaunchedEffect

        if (networkStatus != ConnectivityObserver.Status.Available) {
            activeReelLoadJob.getAndSet(null)?.cancel()
            requestGeneration.incrementAndGet()
            wasEverOffline = true
            atharReels = SilentReelStorageManager.getDownloadedReels(context)
            selectedTab = 2
            selectedCategory = "ALL"
            searchQuery = ""
            isSearchActive = false
            isAlAtharMode = true
            isOfflineMode = true
            canLoadMore = false
            isLoading = false
            isRetrying = false
            loadError = false
        } else {
            if (wasEverOffline) {
                Toast.makeText(context, "عادت الشبكة ✦ يمكنك الآن التنقل بحرية والتفاعل ✨", Toast.LENGTH_LONG).show()
                selectedTab = 0
                isAlAtharMode = false
                isOfflineMode = false
                loadReels()
                wasEverOffline = false
            } else if (allReels.isEmpty()) {
                loadReels()
            }
        }
    }

    /** تحميل الصفحة التالية من الخلاصة (تمرير لانهائي) */
    fun loadMoreReels() {
        if (!isOnline || selectedTab == 2 || isLoadingMore || !canLoadMore || allReels.isEmpty()) return
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
                ReelPreferencesManager.saveCachedReels(context, updatedList)
            }
        }
    }

    // مزامنة قائمة الموثّقين عالمياً لعرض الشارة بشكل صحيح
    LaunchedEffect(isOnline) {
        if (isOnline) runCatching { VerificationManager.refreshFromCloud(context) }
    }

    // Filter reels based on Tab, Category, Search Query and Hidden status
    val sourceReels = if (selectedTab == 2) atharReels else allReels
    // مفاتيح إعادة الترتيب: تتغير بتغيّر البيانات أو إشارات المشاهد، لا مع
    // كل ثانية مشاهدة (watchSeconds.size يتغير فقط عند بدء مقطع جديد).
    val displayReels = remember(sourceReels, hiddenReelIds, selectedTab, selectedCategory, searchQuery, followVersion, likedReelIds, cloudBookmarks, commentBumps, watchSeconds.size) {
        val filtered = sourceReels.filterNot { hiddenReelIds.contains(it.id) }
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
                    2 -> true // وضع الأثر يعرض حصراً المقاطع التي ثبت تنزيلها محلياً.
                    else -> true // For You tab
                }
            }
        // الترتيب (الدفعة 3): «لك» بالنقاط عند وصول البيانات، و«الأحدث»
        // زمني بحت؛ «متابعون» و«الأثر» كما هما.
        when (selectedTab) {
            0 -> {
                val signals = filtered.associate { reel ->
                    reel.id to ReelFeedScoring.ViewerSignals(
                        liked = likedReelIds[reel.id] == true,
                        saved = cloudBookmarks.contains(reel.id),
                        watchSeconds = watchSeconds[reel.id] ?: 0,
                        commentsAdded = commentBumps[reel.id] ?: 0
                    )
                }
                ReelFeedScoring.sortForYou(filtered, signals)
            }
            3 -> filtered.sortedByDescending { it.createdAt.orEmpty() }
            else -> filtered
        }
    }

    val pagerState = rememberPagerState(pageCount = { displayReels.size })
    LaunchedEffect(selectedTab, displayReels.size) {
        if (displayReels.isNotEmpty() && pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    // قفزة موحّدة إلى مقطع محدد (الدفعة 5 — الجزء C؛ مكافئ jumpToReel()
    // من الدفعة 3 التي فُقدت مع فروعها): إعادة ضبط الفلاتر إلى تبويب «لك»،
    // إحضار المقطع من السحابة إن لم يكن محمّلاً، ثم تمرير المتصفح إلى
    // موضعه عبر pendingJumpReelId. مقطع غير موجود أو غير نشط يُبلَّغ
    // عنه برسالة ولا يُفتح مكان خاطئ.
    var pendingJumpReelId by remember { mutableStateOf<String?>(null) }

    fun jumpToReel(targetReelId: String) {
        scope.launch {
            selectedTab = 0
            isAlAtharMode = false
            selectedCategory = "ALL"
            isSearchActive = false
            searchQuery = ""
            hiddenReelIds = hiddenReelIds - targetReelId
            val reel = allReels.firstOrNull { it.id == targetReelId }
                ?: withTimeoutOrNull(10_000L) {
                    SupabaseServices.Profiles.getReelById(targetReelId)
                }
            if (reel == null || !reel.isActive) {
                Toast.makeText(context, "هذا المقطع لم يعد متاحاً للتشغيل.", Toast.LENGTH_LONG).show()
            } else {
                if (allReels.none { it.id == reel.id }) {
                    allReels = allReels + reel
                }
                pendingJumpReelId = targetReelId
            }
        }
    }

    LaunchedEffect(pendingJumpReelId, displayReels) {
        val targetId = pendingJumpReelId ?: return@LaunchedEffect
        val index = displayReels.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            pagerState.scrollToPage(index)
            pendingJumpReelId = null
        }
    }

    // روابط عميقة عند الدخول (الدفعة 5 — الجزء C).
    LaunchedEffect(initialReelId) {
        if (initialReelId != null) jumpToReel(initialReelId)
    }
    LaunchedEffect(initialCreatorId) {
        if (initialCreatorId != null) {
            activeCreatorProfile = Triple(
                initialCreatorId,
                initialCreatorName ?: "صانع محتوى",
                initialCreatorAvatar
            )
        }
    }

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
                } else if (loadError && allReels.isEmpty() && isDeveloper) {
                    // فشل التحميل الصريح: يظهر فقط للمطور مع تشخيص دقيق وزر المثلث التفاعلي
                    ReelsFailureDiagnosticScreen(
                        diagnostic = lastDiagnostic,
                        isRetrying = isRetrying,
                        onRetry = { loadReels(isManualRetry = true) }
                    )
                } else if (selectedTab == 2 && displayReels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(theme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        AppleCard(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .widthIn(max = 420.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(theme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "لا توجد مقاطع أثر محفوظة على الجهاز",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isOnline) "ستظهر المقاطع هنا تلقائياً بعد تنزيلها للمشاهدة دون اتصال." else "لا يمكن جلب المقاطع الآن دون اتصال بالإنترنت.",
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else if (allReels.isEmpty() && selectedTab != 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(theme.background)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            AppleCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 420.dp),
                                cornerRadius = 22.dp
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(theme.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VideoCall,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "لا توجد مقاطع منشورة حالياً",
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = theme.textPrimary,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "كن أول من ينشر مقطعاً قرآنياً أو دعوياً مباركاً ليظهر للمجتمع.",
                                        fontFamily = NotoSansFont,
                                        fontSize = 13.sp,
                                        color = theme.textSecondary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        ApplePrimaryButton(
                                            text = "نشر مقطع الآن",
                                            icon = Icons.Default.Add,
                                            onClick = { checkGuestInteraction { showPublishDialog = true } }
                                        )
                                        AppleSecondaryButton(
                                            text = "تحديث",
                                            icon = Icons.Default.Refresh,
                                            onClick = { loadReels() }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.weight(1.2f))
                        }
                    }
                } else {
                    if (displayReels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(theme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = theme.textSecondary,
                                    modifier = Modifier.size(46.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "لا نتائج مطابقة لبحثك أو تصنيفك الحالي",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                AppleSecondaryButton(
                                    text = "مسح الفلاتر",
                                    icon = Icons.Default.FilterAltOff,
                                    modifier = Modifier.widthIn(max = 220.dp),
                                    onClick = {
                                        selectedCategory = "ALL"
                                        searchQuery = ""
                                        isSearchActive = false
                                    }
                                )
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
                        val isSaved = cloudBookmarks.contains(reel.id)
                        val currentLikes = reel.likesCount

                        val handleToggleLike: () -> Unit = {
                            if (!isAlAtharMode && selectedTab != 2) {
                                checkGuestInteraction {
                                    if (ReelSafetyGuardrails.canPerformLike(reel.id) && likeInFlight[reel.id] != true) {
                                        val newLikedState = !isLiked
                                        likeInFlight[reel.id] = true
                                        likedReelIds[reel.id] = newLikedState
                                        val updatedCount = if (newLikedState) currentLikes + 1 else (currentLikes - 1).coerceAtLeast(0)
                                        allReels = allReels.map { if (it.id == reel.id) it.copy(likesCount = updatedCount) else it }

                                        scope.launch {
                                            val authId = currentSupabaseId
                                            if (authId.isNullOrBlank()) {
                                                likedReelIds[reel.id] = !newLikedState
                                                allReels = allReels.map { if (it.id == reel.id) it.copy(likesCount = currentLikes) else it }
                                                likeInFlight[reel.id] = false
                                                Toast.makeText(context, "تعذر قراءة حالة الحساب الحالية. حاول مرة أخرى.", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }

                                            val ok = runCatching {
                                                withTimeoutOrNull(10_000L) {
                                                    if (newLikedState) {
                                                        SupabaseServices.Profiles.likeReel(reel.id, authId)
                                                    } else {
                                                        SupabaseServices.Profiles.unlikeReel(reel.id, authId)
                                                    }
                                                } ?: false
                                            }.getOrDefault(false)

                                            if (!ok) {
                                                likedReelIds[reel.id] = !newLikedState
                                                allReels = allReels.map { if (it.id == reel.id) it.copy(likesCount = currentLikes) else it }
                                                val errorMsg = if (!SupabaseServices.isSupabaseAvailable) {
                                                    "الخدمات السحابية غير متوفرة حالياً"
                                                } else {
                                                    val raw = SupabaseServices.lastInteractionError ?: ""
                                                    val suffix = if (raw.isNotBlank()) "\n($raw)" else ""
                                                    "تعذر تحديث الإعجاب سحابياً$suffix"
                                                }
                                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                            } else {
                                                // Re-read both sources of truth: the relation colors the heart,
                                                // while the trigger-maintained counter colors the number.
                                                likedReelIds[reel.id] = newLikedState
                                                val cloudCount = withTimeoutOrNull(5_000L) {
                                                    SupabaseServices.Profiles.getReelLikesCount(reel.id)
                                                }
                                                allReels = allReels.map {
                                                    if (it.id == reel.id) {
                                                        it.copy(likesCount = cloudCount ?: updatedCount)
                                                    } else it
                                                }
                                                withTimeoutOrNull(7_000L) {
                                                    refreshLikedReelsFromCloud(authId)
                                                }
                                            }
                                            likeInFlight[reel.id] = false
                                        }
                                    }
                                }
                            }
                        }

                        val handleToggleSave: () -> Unit = {
                            if (!isAlAtharMode && selectedTab != 2) {
                                checkGuestInteraction {
                                    val wasCloudSaved = cloudBookmarks.contains(reel.id)
                                    val nowCloudSaved = !wasCloudSaved
                                    cloudBookmarks = if (nowCloudSaved) cloudBookmarks + reel.id else cloudBookmarks - reel.id
                                    Toast.makeText(
                                        context,
                                        if (nowCloudSaved) "تم حفظ المقطع في المحفوظات السحابية 🔖" else "تمت إزالة المقطع من المحفوظات",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    val authId = SupabaseServices.Auth.getCurrentUserId()
                                    if (authId != null) {
                                        scope.launch {
                                            val ok = runCatching {
                                                withTimeoutOrNull(10_000L) {
                                                    if (nowCloudSaved) {
                                                        SupabaseServices.Profiles.addReelBookmark(reel.id, authId)
                                                    } else {
                                                        SupabaseServices.Profiles.removeReelBookmark(reel.id, authId)
                                                    }
                                                } ?: false
                                            }.getOrDefault(false)
                                            if (!ok) {
                                                cloudBookmarks = if (wasCloudSaved) cloudBookmarks + reel.id else cloudBookmarks - reel.id
                                                Toast.makeText(context, "تعذر مزامنة المحفوظات مع السحابة", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val isLocalAtharReel = selectedTab == 2
                        val isNetworkPermitted = remember(autoplayWifiOnly, isOnline, isLocalAtharReel) {
                            isLocalAtharReel || (isOnline && ReelSafetyGuardrails.isNetworkPermittedForAutoplay(context, autoplayWifiOnly))
                        }
                        val playbackUrl = remember(reel.id, reel.videoUrl, isLocalAtharReel) {
                            if (isLocalAtharReel) {
                                SilentReelStorageManager.getLocalVideoUri(context, reel.id)?.toString().orEmpty()
                            } else {
                                reel.videoUrl
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            val aspectRatio = remember(reel.metadata) {
                                reel.metadata?.get("aspect_ratio")?.toString()?.trim('"') ?: "9:16"
                            }
                            
                            ReelPlayer(
                                videoUrl = playbackUrl,
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
                                    if (selectedTab == 2 || isAlAtharMode) {
                                        // وضع الأثر مخصص للمشاهدة فقط — لا يوجد تفاعل
                                    } else {
                                        checkGuestInteraction {
                                            when (doubleTapAction) {
                                                "SAVE" -> handleToggleSave()
                                                else -> if (!isLiked) handleToggleLike()
                                            }
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
                                    if (!isAlAtharMode && selectedTab != 2) {
                                        checkGuestInteraction { activeCommentReel = reel }
                                    }
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
                                                    "شاهد مقطع «${reel.title}» المميز عبر استوديو خادم المسلم: ${reel.videoUrl}"
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
                                },
                                isAlAtharMode = isAlAtharMode || selectedTab == 2
                            )
                        }
                    }
                }

                // «مرصد خادم المسلم الهندسي» — collapsible geometric dock with all screen tools
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
                                label = if (isDeveloper) "نشر مطور" else "نشر",
                                onClick = {
                                    checkGuestInteraction {
                                        if (!currentSupabaseId.isNullOrBlank()) {
                                            showPublishDialog = true
                                        } else {
                                            Toast.makeText(context, "تعذر قراءة حالة الحساب. أعد المحاولة.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    ReelPreferencesManager.triggerHaptic(context)
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
                                accent = theme.destructive
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

                // Top Bar over full-bleed player (Apple frosted translucent chrome)
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
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xA61C1C1E))
                                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f)), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Center Apple Frosted Segmented Pill (لك | متابعون | الأثر)
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xB81C1C1E),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)),
                                modifier = Modifier.heightIn(min = 42.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            if (!isOnline) {
                                                Toast.makeText(context, "أنت في وضع الأثر (دون اتصال) — يتطلب اتصالاً بالإنترنت", Toast.LENGTH_SHORT).show()
                                            } else {
                                                selectedTab = 0
                                                isAlAtharMode = false
                                            }
                                            ReelPreferencesManager.triggerHaptic(context)
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (selectedTab == 0) theme.primary else Color.Transparent,
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (!isOnline) "لك (أوفلاين)" else "لك",
                                                fontFamily = CairoFont,
                                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                softWrap = false,
                                                maxLines = 1,
                                                color = if (selectedTab == 0) theme.onPrimary else Color.White.copy(alpha = if (!isOnline) 0.45f else 0.85f)
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            if (!isOnline) {
                                                Toast.makeText(context, "أنت في وضع الأثر (دون اتصال) — يتطلب اتصالاً بالإنترنت", Toast.LENGTH_SHORT).show()
                                            } else {
                                                selectedTab = 3
                                                isAlAtharMode = false
                                            }
                                            ReelPreferencesManager.triggerHaptic(context)
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (selectedTab == 3) theme.primary else Color.Transparent,
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "الأحدث",
                                                fontFamily = CairoFont,
                                                fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                softWrap = false,
                                                maxLines = 1,
                                                color = if (selectedTab == 3) theme.onPrimary else Color.White.copy(alpha = if (!isOnline) 0.45f else 0.85f)
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            if (!isOnline) {
                                                Toast.makeText(context, "أنت في وضع الأثر (دون اتصال) — يتطلب اتصالاً بالإنترنت", Toast.LENGTH_SHORT).show()
                                            } else {
                                                checkGuestInteraction {
                                                    selectedTab = 1
                                                    isAlAtharMode = false
                                                    ReelPreferencesManager.triggerHaptic(context)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (selectedTab == 1) theme.primary else Color.Transparent,
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "متابعون",
                                                fontFamily = CairoFont,
                                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                softWrap = false,
                                                maxLines = 1,
                                                color = if (selectedTab == 1) theme.onPrimary else Color.White.copy(alpha = if (!isOnline) 0.45f else 0.85f)
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            atharReels = SilentReelStorageManager.getDownloadedReels(context)
                                            selectedTab = 2
                                            selectedCategory = "ALL"
                                            searchQuery = ""
                                            isSearchActive = false
                                            isAlAtharMode = true
                                            ReelPreferencesManager.triggerHaptic(context)
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (selectedTab == 2) theme.primary else Color.Transparent,
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "الأثر",
                                                fontFamily = CairoFont,
                                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                softWrap = false,
                                                maxLines = 1,
                                                color = if (selectedTab == 2) theme.onPrimary else Color.White.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }

                            // زر صفحة «استكشف» (الدفعة 3): شبكة المصغّرات والوسوم والأصوات.
                            IconButton(
                                onClick = { showExplore = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xA61C1C1E))
                                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f)), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "استكشف الريلز",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Quiet Al-Athar / Offline indicator pill
                        AnimatedVisibility(
                            visible = !isOnline || isAlAtharMode,
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
                                    onClick = {
                                        if (isOnline) {
                                            loadReels(true)
                                        } else {
                                            Toast.makeText(context, "وضع الأثر نشط للمشاهدة الهادئة دون اتصال", Toast.LENGTH_SHORT).show()
                                        }
                                        ReelPreferencesManager.triggerHaptic(context)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xCC1C1C1E),
                                    border = BorderStroke(0.5.dp, theme.primary.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Spa,
                                            contentDescription = null,
                                            tint = theme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (!isOnline) "وضع الأثر الإجباري • مشاهدة دون اتصال" else "وضع الأثر • مساحة للسكينة والتدبر",
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        if (isOnline) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "إعادة الاتصال",
                                                tint = theme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Expandable Search & Categories
                        AnimatedVisibility(
                            visible = isSearchActive,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Surface(
                                color = theme.cardSurface.copy(alpha = 0.96f),
                                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                                shadowElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = {
                                            Text(
                                                "ابحث عن تلاوة، شيخ، أو موضوع...",
                                                fontFamily = NotoSansFont,
                                                fontSize = 13.sp,
                                                color = theme.textSecondary
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = theme.textSecondary)
                                        },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { searchQuery = "" },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Default.Clear, contentDescription = "مسح", tint = theme.textSecondary)
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.primary,
                                            unfocusedBorderColor = theme.separator.copy(alpha = 0.45f),
                                            focusedTextColor = theme.textPrimary,
                                            unfocusedTextColor = theme.textPrimary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val categories = listOf(
                                            "ALL" to "الكل",
                                            "QURAN" to "تلاوات قرآنية",
                                            "HADITH" to "أحاديث نبوية",
                                            "STORIES" to "قصص وعبر"
                                        )
                                        items(categories) { (key, label) ->
                                            val isSelected = selectedCategory == key
                                            Surface(
                                                onClick = {
                                                    selectedCategory = key
                                                    ReelPreferencesManager.triggerHaptic(context)
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (isSelected) theme.primary else theme.secondarySurface,
                                                border = BorderStroke(
                                                    width = if (isSelected) 0.dp else 0.5.dp,
                                                    color = theme.separator.copy(alpha = 0.45f)
                                                ),
                                                modifier = Modifier.heightIn(min = 40.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontFamily = CairoFont,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) theme.onPrimary else theme.textPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        onClick = { isKhushuMode = false },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xB81C1C1E),
                        border = BorderStroke(0.5.dp, theme.primary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(top = 12.dp)
                            .heightIn(min = 48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "وضع الخشوع مفعل • انقر للعودة",
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
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
            currentUserName = viewer.displayName,
            onCommentPosted = {
                commentBumps = commentBumps + (reel.id to (commentBumps[reel.id] ?: 0) + 1)
            },
            onCommentDeleted = {
                val nextDelta = ((commentBumps[reel.id] ?: 0) - 1).coerceAtLeast(-reel.commentsCount)
                commentBumps = if (nextDelta == 0) commentBumps - reel.id else commentBumps + (reel.id to nextDelta)
            },
            onDismiss = { activeCommentReel = null }
        )
    }

    // More Options Bottom Sheet
    activeMoreOptionsReel?.let { reel ->
        val isSaved = cloudBookmarks.contains(reel.id)
        ReelMoreOptionsSheet(
            reel = reel,
            isSaved = isSaved,
            currentUserEmail = currentUserEmail,
            currentUserId = currentUserId,
            onToggleSave = {
                val authId = SupabaseServices.Auth.getCurrentUserId()
                if (authId.isNullOrBlank()) {
                    Toast.makeText(context, "سجّل الدخول لحفظ الريل في المحفوظات السحابية", Toast.LENGTH_SHORT).show()
                } else {
                val wasCloudSaved = cloudBookmarks.contains(reel.id)
                val nowCloudSaved = !wasCloudSaved
                cloudBookmarks = if (nowCloudSaved) cloudBookmarks + reel.id else cloudBookmarks - reel.id
                Toast.makeText(
                    context,
                    if (nowCloudSaved) "تم حفظ المقطع في المحفوظات السحابية 🔖" else "تمت إزالة المقطع من المحفوظات",
                    Toast.LENGTH_SHORT
                ).show()

                if (authId != null) {
                    scope.launch {
                        val ok = runCatching {
                            withTimeoutOrNull(10_000L) {
                                if (nowCloudSaved) SupabaseServices.Profiles.addReelBookmark(reel.id, authId)
                                else SupabaseServices.Profiles.removeReelBookmark(reel.id, authId)
                            } ?: false
                        }.getOrDefault(false)
                        if (!ok) {
                            cloudBookmarks = if (wasCloudSaved) cloudBookmarks + reel.id else cloudBookmarks - reel.id
                        }
                    }
                }
                }
            },
            onSpeedSelected = { newSpeed ->
                ReelPreferencesManager.setPlaybackSpeed(context, newSpeed)
                Toast.makeText(context, "تم ضبط سرعة التشغيل على ${newSpeed}x", Toast.LENGTH_SHORT).show()
            },
            onNotInterested = {
                hiddenReelIds = ReelPreferencesManager.hideReel(context, reel.id)
                Toast.makeText(context, "تم إخفاء المقطع. يمكنك إدارة المحتوى المخفي من إعدادات الريلز", Toast.LENGTH_SHORT).show()
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
                if (reportedReelIds.contains(reel.id)) {
                    Toast.makeText(context, "تم إرسال بلاغ لهذا المقطع مسبقًا", Toast.LENGTH_SHORT).show()
                } else {
                    reportedReelIds = reportedReelIds + reel.id
                    scope.launch {
                        val sent = runCatching {
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
                        }.isSuccess
                        if (!sent) reportedReelIds = reportedReelIds - reel.id
                        Toast.makeText(
                            context,
                            if (sent) "تم إرسال البلاغ وسيتم مراجعته من فريق الإشراف" else "تعذر إرسال البلاغ، حاول مرة أخرى",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onDismiss = { activeMoreOptionsReel = null }
        )
    }

    LaunchedEffect(showPublishDialog, accountAuthState) {
        if (showPublishDialog && accountAuthState !is AuthState.Authenticated) {
            showPublishDialog = false
            if (accountAuthState is AuthState.Guest || accountAuthState is AuthState.Unauthenticated) {
                showGuestLoginPrompt = true
            } else {
                Toast.makeText(context, "تعذر تجهيز حالة المصادقة مؤقتاً. حاول مرة أخرى.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Publish Reel Screen
    if (showPublishDialog && currentSupabaseId != null) {
        val authId = currentSupabaseId
        ReelPublishScreen(
                currentUserId = authId,
                currentUserName = currentUserName,
                currentUserEmail = currentUserEmail,
                onDismiss = { showPublishDialog = false },
                onPublishedSuccess = {
                    loadReels()
                },
                onRequireLogin = {
                    showPublishDialog = false
                    showGuestLoginPrompt = true
                }
            )
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        ReelSettingsSheet(
            onDismiss = { showSettingsSheet = false },
            onHiddenReelsCleared = { hiddenReelIds = emptySet() }
        )
    }

    // History & Activity Bottom Sheet
    if (showHistorySheet) {
        ReelHistorySheet(
            onDismiss = { showHistorySheet = false },
            // القفزة الموحّدة نفسها (الدفعة 3) بدل منطق مكرر هنا.
            onSelectReel = { targetReelId -> jumpToReel(targetReelId) }
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
            liveViews = reel.viewsCount,
            liveLikes = reel.likesCount,
            onOpenCreatorProfile = { id, name, avatar ->
                activeDetailsReel = null
                activeCreatorProfile = Triple(id, name, avatar)
            },
            onDismiss = { activeDetailsReel = null },
            isAlAtharMode = isAlAtharMode || selectedTab == 2
        )
    }

    // Audio / Recitation Details Bottom Sheet
    activeAudioReel?.let { reel ->
        ReelAudioDetailsSheet(
            reel = reel,
            onDismiss = { activeAudioReel = null }
        )
    }


    // 🛡️ Guest Interaction Dialog
    if (showGuestLoginPrompt) {
        GuestInteractionDialog(
            onDismiss = {
                showGuestLoginPrompt = false
                pendingInteractionAction = null
            },
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

    // صفحة «استكشف» (الدفعة 3) فوق الخلاصة؛ إغلاقها يعود للخلاصة نفسها،
    // والنقر على مقطع يقفز إليه فيها عبر jumpToReel.
    if (showExplore) {
        ReelsExploreScreen(
            reels = allReels,
            onBack = { showExplore = false },
            onJumpToReel = { reelId ->
                showExplore = false
                jumpToReel(reelId)
            },
            onOpenCreator = { creatorId, creatorName, creatorAvatar ->
                showExplore = false
                activeCreatorProfile = Triple(creatorId, creatorName, creatorAvatar)
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
    val theme = QabasTheme.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AppleCard(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "تسجيل الدخول",
                        tint = theme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "تسجيل الدخول مطلوب للتفاعل",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = theme.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "لتتمكن من الإعجاب بالمقاطع ومشاركة التعليقات وحفظ المفضلة ومتابعة القرّاء، يرجى تسجيل الدخول بحسابك.",
                    fontFamily = NotoSansFont,
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ApplePrimaryButton(
                        text = "تسجيل الدخول بحسابك",
                        icon = Icons.Default.Lock,
                        onClick = onLogin
                    )
                    AppleSecondaryButton(
                        text = "المتابعة كضيف",
                        onClick = onDismiss
                    )
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
    onOpenCreatorProfile: (creatorId: String, creatorName: String, creatorAvatar: String?) -> Unit,
    isAlAtharMode: Boolean = false
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

    val theme = QabasTheme.current

    if (isKhushuMode) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Bottom Meta Info (audio track pill)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 66.dp, bottom = 96.dp)
                .fillMaxWidth()
        ) {
            Surface(
                onClick = onAudioClick,
                color = Color(0xA61C1C1E),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "الصوت الأصلي للمقطع",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Side interaction rail (hidden in Al-Athar mode)
        if (!isAlAtharMode) {
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
                        ReelRailButton(
                            icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            label = formatCount(likesCount),
                            tint = if (isLiked) theme.destructive else Color.White,
                            onClick = onLike,
                            contentOverride = { QabasPulseIcon(isActive = isLiked, activeTint = theme.primary) }
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
                            tint = if (isSaved) theme.primary else Color.White,
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
                            tint = Color.White.copy(alpha = 0.9f),
                            onClick = onMoreOptions
                        )

                        // Spinning audio disc
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { onAudioClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC1C1C1E))
                                    .border(1.5.dp, theme.primary.copy(alpha = 0.7f), CircleShape)
                                    .rotate(rotation),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(theme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom corner toggle buttons
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
            if (!isAlAtharMode) {
                ReelArrowButton(
                    expanded = actionsExpanded,
                    label = "التفاعل",
                    onClick = { actionsExpanded = !actionsExpanded }
                )
            }
        }
    }
}

@Composable
fun ReelArrowButton(
    expanded: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(50.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xB81C1C1E))
                .border(0.5.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = label,
                tint = theme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontFamily = CairoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

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
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xB81C1C1E))
                .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)), CircleShape)
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
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun ReelRailButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    contentOverride: (@Composable () -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xB81C1C1E))
                .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (contentOverride != null) {
                contentOverride()
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = label,
            color = Color.White,
            fontFamily = CairoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun QabasPulseIcon(
    isActive: Boolean,
    activeTint: Color = Color(0xFF0A84FF)
) {
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            pulseScale.snapTo(0.82f)
            pulseScale.animateTo(1.08f, animationSpec = tween(180, easing = FastOutSlowInEasing))
            pulseScale.animateTo(1f, animationSpec = tween(160, easing = FastOutSlowInEasing))
        } else {
            pulseScale.snapTo(1f)
        }
    }

    Canvas(
        modifier = Modifier
            .size(21.dp)
            .scale(pulseScale.value)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.34f
        val glow = if (isActive) activeTint else Color.White.copy(alpha = 0.72f)
        val stroke = Stroke(width = size.minDimension * 0.095f, cap = StrokeCap.Round)
        val crescent = Path().apply {
            moveTo(center.x + radius * 0.25f, center.y - radius)
            cubicTo(
                center.x - radius * 1.05f, center.y - radius * 0.8f,
                center.x - radius * 1.05f, center.y + radius * 0.8f,
                center.x + radius * 0.25f, center.y + radius
            )
            cubicTo(
                center.x - radius * 0.18f, center.y + radius * 0.55f,
                center.x - radius * 0.18f, center.y - radius * 0.55f,
                center.x + radius * 0.25f, center.y - radius
            )
            close()
        }
        if (isActive) {
            drawPath(crescent, color = glow)
            drawCircle(glow, radius = size.minDimension * 0.08f, center = Offset(center.x + radius * 1.25f, center.y - radius * 0.72f))
            drawCircle(Color.White, radius = size.minDimension * 0.06f, center = center)
        } else {
            drawPath(crescent, color = glow, style = stroke)
            drawCircle(glow, radius = size.minDimension * 0.055f, center = Offset(center.x + radius * 1.25f, center.y - radius * 0.72f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsBottomSheet(
    reel: SupabaseServices.ReelRow,
    currentUserId: String,
    currentUserName: String = "مستخدم خادم المسلم",
    onCommentPosted: () -> Unit = {},
    onCommentDeleted: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    var commentText by remember { mutableStateOf("") }
    var commentsList by remember { mutableStateOf<List<SupabaseServices.ReelCommentRow>>(emptyList()) }
    var isPosting by remember { mutableStateOf(false) }
    var commentFeedback by remember { mutableStateOf<String?>(null) }
    var commentFeedbackIsError by remember { mutableStateOf(false) }
    var expandedCommentMenuId by remember { mutableStateOf<String?>(null) }
    var editingComment by remember { mutableStateOf<SupabaseServices.ReelCommentRow?>(null) }
    var editCommentText by remember { mutableStateOf("") }
    var pendingDeleteComment by remember { mutableStateOf<SupabaseServices.ReelCommentRow?>(null) }
    var managingCommentId by remember { mutableStateOf<String?>(null) }
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
            Toast.makeText(context, "يرجى الانتظار قليلاً قبل إرسال تعليق آخر", Toast.LENGTH_SHORT).show()
            return@submit
        }
        isPosting = true
        commentFeedback = "جارٍ إرسال التعليق..."
        commentFeedbackIsError = false
        coroutineScope.launch {
            val inserted = runCatching {
                withTimeoutOrNull(12_000L) {
                    SupabaseServices.Profiles.addReelComment(reel.id, currentUserId, currentUserName, sanitized)
                }
            }.getOrNull()
            if (inserted != null) {
                commentsList = commentsList + inserted
                commentText = ""
                commentFeedback = "تم نشر تعليقك بنجاح"
                commentFeedbackIsError = false
                onCommentPosted()
                Toast.makeText(context, "تم نشر التعليق بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                commentFeedback = "تعذر إرسال التعليق. تحقق من الاتصال ثم أعد المحاولة."
                commentFeedbackIsError = true
                Toast.makeText(context, "تعذر إرسال التعليق حالياً", Toast.LENGTH_SHORT).show()
            }
            isPosting = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.cardSurface,
        contentColor = theme.textPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = theme.separator)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .fillMaxHeight(0.62f)
        ) {
            Text(
                text = "التعليقات (${commentsList.size})",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.5f))

            if (commentsLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            } else if (commentsError) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "تعذر تحميل التعليقات",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 14.sp
                    )
                }
            } else if (commentsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "كن أول من يشارك بتعليق طيب",
                        color = theme.textSecondary,
                        fontFamily = CairoFont,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(commentsList, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(theme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = c.userName.trim().firstOrNull()?.toString() ?: "م",
                                    color = theme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = c.userName.ifBlank {
                                            if (c.userId == currentUserId) currentUserName else "مستخدم خادم المسلم"
                                        },
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    c.userCustomId?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }?.let { customId ->
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "@$customId",
                                            color = theme.primary,
                                            fontFamily = RobotoMonoFont,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (c.userId == currentUserId) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "أنت",
                                            color = theme.primary,
                                            fontFamily = CairoFont,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (!c.updatedAt.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "تم التعديل",
                                            color = theme.textSecondary,
                                            fontFamily = NotoSansFont,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Text(
                                    text = c.commentText,
                                    color = theme.textPrimary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 13.5.sp,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            if (currentUserId.isNotBlank() && c.userId == currentUserId) {
                                Box {
                                    IconButton(
                                        onClick = { expandedCommentMenuId = c.id },
                                        enabled = managingCommentId == null,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "إدارة التعليق",
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = expandedCommentMenuId == c.id,
                                        onDismissRequest = { expandedCommentMenuId = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("تعديل التعليق", fontFamily = CairoFont) },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                expandedCommentMenuId = null
                                                editingComment = c
                                                editCommentText = c.commentText
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("حذف التعليق", fontFamily = CairoFont, color = theme.destructive) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = theme.destructive) },
                                            onClick = {
                                                expandedCommentMenuId = null
                                                pendingDeleteComment = c
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(SPIRITUAL_REACTIONS.take(4)) { reaction ->
                    Surface(
                        onClick = { if (!isPosting) submitComment("${reaction.icon} ${reaction.text}") },
                        shape = RoundedCornerShape(14.dp),
                        color = theme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, theme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${reaction.icon} ${reaction.text}",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = theme.primary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = {
                        commentText = it
                        commentFeedback = null
                    },
                    placeholder = {
                        Text("اكتب تعليقاً طيباً...", fontFamily = NotoSansFont, fontSize = 13.sp, color = theme.textSecondary)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.separator.copy(alpha = 0.5f),
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedContainerColor = theme.secondarySurface.copy(alpha = 0.5f),
                        unfocusedContainerColor = theme.secondarySurface.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { submitComment(commentText) },
                    enabled = commentText.isNotBlank() && !isPosting,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (commentText.isNotBlank()) theme.primary else theme.secondarySurface,
                            CircleShape
                        )
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            color = theme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال",
                            tint = if (commentText.isNotBlank()) theme.onPrimary else theme.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            commentFeedback?.let { feedback ->
                Text(
                    text = feedback,
                    color = if (commentFeedbackIsError) theme.destructive else theme.success,
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    editingComment?.let { target ->
        AlertDialog(
            onDismissRequest = { if (managingCommentId == null) editingComment = null },
            containerColor = theme.cardSurface,
            titleContentColor = theme.textPrimary,
            textContentColor = theme.textPrimary,
            title = { Text("تعديل التعليق", fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editCommentText,
                    onValueChange = { editCommentText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    label = { Text("نص التعليق", fontFamily = CairoFont) }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editCommentText.isNotBlank() && managingCommentId == null,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        val safeText = ReelSafetyGuardrails.sanitizeCommentText(editCommentText) ?: return@TextButton
                        managingCommentId = target.id
                        coroutineScope.launch {
                            val updated = runCatching {
                                withTimeoutOrNull(12_000L) {
                                    SupabaseServices.Profiles.updateReelComment(target.id, currentUserId, safeText)
                                }
                            }.getOrNull()
                            if (updated != null) {
                                commentsList = commentsList.map { if (it.id == updated.id) updated else it }
                                editingComment = null
                                Toast.makeText(context, "تم تحديث التعليق بنجاح", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "تعذر تعديل التعليق، حاول مرة أخرى", Toast.LENGTH_SHORT).show()
                            }
                            managingCommentId = null
                        }
                    }
                ) {
                    if (managingCommentId == target.id) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("حفظ", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.primary)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = managingCommentId == null,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { editingComment = null }
                ) { Text("إلغاء", fontFamily = CairoFont, color = theme.textSecondary) }
            }
        )
    }

    pendingDeleteComment?.let { target ->
        AlertDialog(
            onDismissRequest = { if (managingCommentId == null) pendingDeleteComment = null },
            containerColor = theme.cardSurface,
            titleContentColor = theme.textPrimary,
            textContentColor = theme.textSecondary,
            title = { Text("حذف التعليق؟", fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = { Text("سيتم حذف تعليقك نهائياً من هذا المقطع.", fontFamily = NotoSansFont) },
            confirmButton = {
                TextButton(
                    enabled = managingCommentId == null,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        managingCommentId = target.id
                        coroutineScope.launch {
                            val deleted = runCatching {
                                withTimeoutOrNull(12_000L) {
                                    SupabaseServices.Profiles.deleteReelComment(target.id, currentUserId)
                                } ?: false
                            }.getOrDefault(false)
                            if (deleted) {
                                commentsList = commentsList.filterNot { it.id == target.id }
                                pendingDeleteComment = null
                                onCommentDeleted()
                                Toast.makeText(context, "تم حذف التعليق", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "تعذر حذف التعليق، حاول مرة أخرى", Toast.LENGTH_SHORT).show()
                            }
                            managingCommentId = null
                        }
                    }
                ) {
                    if (managingCommentId == target.id) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("حذف", fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.destructive)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = managingCommentId == null,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { pendingDeleteComment = null }
                ) { Text("إلغاء", fontFamily = CairoFont, color = theme.textSecondary) }
            }
        )
    }
}


/**
 * Apple HIG Clean Loading View for Reels Feed
 */
@Composable
fun ReelsInteractiveLoadingView(
    progress: Float,
    onRetry: () -> Unit
) {
    val theme = QabasTheme.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "reelLoadingProgress"
    )
    val percentInt = (animatedProgress * 100).toInt()

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
            .background(theme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    color = theme.primary,
                    trackColor = theme.separator.copy(alpha = 0.3f),
                    strokeWidth = 5.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "$percentInt%",
                    color = theme.textPrimary,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "جاري تحميل المقاطع...",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .clip(CircleShape),
                color = theme.primary,
                trackColor = theme.separator.copy(alpha = 0.35f)
            )

            if (waitTimerSeconds >= 5) {
                Spacer(modifier = Modifier.height(18.dp))
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إعادة المحاولة",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * شاشة التشخيص الدقيق عند فشل جلب مقاطع الريلز من السحابة
 */
@Composable
fun ReelsFailureDiagnosticScreen(
    diagnostic: SupabaseServices.ReelsFetchDiagnosticResult?,
    isRetrying: Boolean,
    onRetry: () -> Unit
) {
    val theme = QabasTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ReelsDiagnosticCard(
                diagnostic = diagnostic,
                isRetrying = isRetrying
            )

            InfiniteTriangleRetryButton(
                latencyMs = diagnostic?.latencyMs,
                isRetrying = isRetrying,
                onRetry = onRetry
            )
        }
    }
}

@Composable
fun ReelsDiagnosticCard(
    diagnostic: SupabaseServices.ReelsFetchDiagnosticResult?,
    isRetrying: Boolean
) {
    val theme = QabasTheme.current
    val httpCode = diagnostic?.httpCode
    val stage = diagnostic?.errorStage ?: "NETWORK"
    val latency = diagnostic?.latencyMs ?: 0L
    val isCircuit = diagnostic?.isCircuitBreakerActive == true
    val cooldownSec = diagnostic?.remainingCooldownSec ?: 0

    val statusColor = when {
        isRetrying -> theme.primary
        httpCode == 200 -> theme.success
        isCircuit -> theme.warning
        else -> theme.destructive
    }

    val statusTitle = when {
        isRetrying -> "جاري الفحص المباشر للسحابة..."
        isCircuit -> "قاطع الدائرة نشط (حماية التردد)"
        httpCode == 401 || httpCode == 403 -> "خطأ في مصادقة مفتاح Supabase"
        stage == "TIMEOUT" -> "انتهت مهلة استجابة الخادم"
        stage == "CONFIG" -> "السحابة غير مهيأة"
        else -> "تعذر استلام مقاطع الخلاصة"
    }

    AppleCard(cornerRadius = 18.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
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
                        text = "تشخيص السحابة",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = theme.textPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (httpCode != null && httpCode > 0) "HTTP $httpCode" else stage,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricBadge(
                    modifier = Modifier.weight(1f),
                    label = "الاستجابة",
                    value = if (latency > 0) "${latency}ms" else "--",
                    subValue = when {
                        latency in 1..250 -> "سريع"
                        latency in 251..650 -> "متوسط"
                        latency > 650 -> "بطيء"
                        else -> "منقطع"
                    },
                    accentColor = when {
                        latency in 1..250 -> theme.success
                        latency in 251..650 -> theme.warning
                        else -> theme.destructive
                    }
                )

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
                    accentColor = if (httpCode == 200) theme.success else theme.destructive
                )

                MetricBadge(
                    modifier = Modifier.weight(1f),
                    label = "الحماية",
                    value = if (isCircuit) "${cooldownSec}s" else "جاهز",
                    subValue = if (isCircuit) "مؤمّن" else "طبيعي",
                    accentColor = if (isCircuit) theme.warning else theme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = theme.secondarySurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusTitle,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = theme.textPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "محمي",
                                tint = theme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "المسارات محمية",
                                fontFamily = NotoSansFont,
                                fontSize = 10.5.sp,
                                color = theme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val rawMsg = diagnostic?.errorMessage ?: "تحقق من اتصال جهازك بالإنترنت أو أعد المحاولة لتجاوز التهدئة."
                    val maskedMsg = rawMsg
                        .replace(Regex("https?://[^\\s'\"]+"), "[مسار سحابي محمي]")
                        .replace(Regex("\"[a-z0-9]+\\.supabase\\.co\""), "\"[نطاق السحابة]\"")
                    Text(
                        text = maskedMsg,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        lineHeight = 18.sp
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
    val theme = QabasTheme.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = theme.secondarySurface,
        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                color = theme.textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subValue,
                fontFamily = NotoSansFont,
                fontSize = 10.5.sp,
                color = theme.textPrimary
            )
        }
    }
}

@Composable
fun InfiniteTriangleRetryButton(
    latencyMs: Long?,
    isRetrying: Boolean,
    onRetry: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(
        onClick = if (isRetrying) null else onRetry,
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(theme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        color = theme.primary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "إعادة المحاولة",
                        tint = theme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRetrying) "جاري فحص السحابة..." else "إعادة المحاولة وفحص الاتصال",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = theme.textPrimary
                )
                Text(
                    text = if (isRetrying) "تصفير قاطع الدائرة واستعلام الخادم" else "تصفير الحماية واختبار سرعة الاستجابة",
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp,
                    color = theme.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.secondarySurface
            ) {
                Text(
                    text = if (latencyMs != null && latencyMs > 0) "${latencyMs}ms" else "فحص",
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = theme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}


/**
 * 🛰️ ReelDetailsBottomSheet — لوحة تفاصيل المقطع الهندسية الحديثة (العنوان، التصنيف، الوصف، والناشر)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelDetailsBottomSheet(
    reel: SupabaseServices.ReelRow,
    liveViews: Int = reel.viewsCount,
    liveLikes: Int = reel.likesCount,
    onOpenCreatorProfile: (String, String, String?) -> Unit,
    onDismiss: () -> Unit,
    isAlAtharMode: Boolean = false
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
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
        containerColor = theme.cardSurface,
        contentColor = theme.textPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = theme.separator)
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            AppleCard(
                containerColor = theme.secondarySurface,
                cornerRadius = 16.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, theme.primary.copy(alpha = 0.55f), CircleShape)
                            .clickable {
                                onOpenCreatorProfile(reel.userId, reel.userName ?: "صانع محتوى خادم المسلم", reel.userAvatar)
                            }
                    ) {
                        AsyncImage(
                            model = reel.userAvatar ?: "https://ui-avatars.com/api/?name=${reel.userName}&background=007AFF&color=FFFFFF",
                            contentDescription = "صورة صانع المحتوى",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = reel.userName ?: "صانع محتوى خادم المسلم",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (creatorVerified) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "موثق",
                                    tint = theme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "نشر ${formatRelativeTime(reel.createdAt)}",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
                            )
                        }
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
                                containerColor = if (isFollowingCreator) theme.cardSurface else theme.primary,
                                contentColor = if (isFollowingCreator) theme.textPrimary else theme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = if (isFollowingCreator) "متابَع" else "متابعة",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = reel.title,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 26.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (reel.tags.any { it.isNotBlank() }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(reel.tags.filter { it.isNotBlank() }) { rawTag ->
                        val cleanTag = rawTag.trim().removePrefix("#")
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = theme.primary.copy(alpha = 0.10f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "#$cleanTag",
                                    color = theme.primary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (!reel.description.isNullOrBlank()) {
                AppleCard(containerColor = theme.secondarySurface) {
                    Text(
                        text = reel.description,
                        color = theme.textPrimary,
                        fontFamily = NotoSansFont,
                        fontSize = 13.5.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (isAlAtharMode) {
                AppleCard(containerColor = theme.primary.copy(alpha = 0.10f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Spa,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "وضع الأثر • مساحة للسكينة والتدبر",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = theme.primary
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GeometricStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Visibility,
                        count = liveViews,
                        label = "مشاهدة",
                        accentColor = theme.primary
                    )
                    GeometricStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Favorite,
                        count = liveLikes,
                        label = "إعجاب",
                        accentColor = theme.destructive
                    )
                    GeometricStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Comment,
                        count = reel.commentsCount,
                        label = "تعليق",
                        accentColor = theme.success
                    )
                }
            }
        }
    }
}

@Composable
fun GeometricStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    count: Int,
    label: String,
    accentColor: Color
) {
    val theme = QabasTheme.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = theme.secondarySurface,
        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatCompactCount(count),
                color = theme.textPrimary,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = label,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 11.5.sp
            )
        }
    }
}

private fun formatCompactCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.ROOT, "%.1fK", count / 1000.0)
        else -> count.toString()
    }
}

@Composable
fun AlAtharExplanationDialog(
    onDismiss: () -> Unit,
    onEnable: () -> Unit
) {
    val theme = QabasTheme.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppleCard(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 440.dp),
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(theme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "ميزة وضع الأثر",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = theme.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "عند تفعيل هذه الميزة، يقوم التطبيق بتحميل مجموعة من المقاطع الإيمانية المختارة لتتمكن من مشاهدتها حتى عند انقطاع الإنترنت.",
                    fontFamily = NotoSansFont,
                    fontSize = 13.5.sp,
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ApplePrimaryButton(
                        text = "تفعيل الآن",
                        icon = Icons.Default.CloudDownload,
                        onClick = onEnable
                    )
                    AppleSecondaryButton(
                        text = "ليس الآن",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, label: String) {
    val theme = QabasTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
    }
}
