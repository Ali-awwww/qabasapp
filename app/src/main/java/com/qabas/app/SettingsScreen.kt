package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleGroupedSection
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VIDEO_CACHE_DIRECTORY = "qabas_video_cache"

private fun cacheBytes(context: Context): Long = sequenceOf(context.cacheDir, context.externalCacheDir)
    .filterNotNull()
    .distinctBy { it.absolutePath }
    .sumOf { root ->
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

private fun clearTemporaryCache(context: Context) {
    sequenceOf(context.cacheDir, context.externalCacheDir)
        .filterNotNull()
        .distinctBy { it.absolutePath }
        .forEach { root ->
            root.listFiles()?.forEach { child ->
                if (child.name != VIDEO_CACHE_DIRECTORY) child.deleteRecursively()
            }
            root.mkdirs()
        }
}

/**
 * شاشات الإعدادات الفرعية المستقلة (Dedicated Sub-Screens Architecture)
 */
enum class SettingsSubScreen {
    MAIN,
    UX_DESIGN_STUDIO,
    SOUND_STUDIO,
    VIDEO_PLAYER_SETTINGS,
    SPIRITUAL_STUDIO,
    PRO_PRODUCTION_HUB,
    COMMUNITY_SUPPORT,
    CYBER_HUD_PREVIEW,
    PERMISSIONS_HUB,
    CRASH_LOGS,
    NOTIFICATION_SETTINGS,
    GITHUB_XDELTA_HUB,
    BUG_CAMERA_CONTROL,
    ACCESSIBILITY
}

data class SearchableSetting(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val subScreen: SettingsSubScreen,
    val tags: List<String> = emptyList(),
    val badge: String? = null,
    val badgeColor: Color? = null,
    val accentColor: Color = PresetPrimary,
    val group: String = "عام"
)

internal sealed class SettingsItemAction {
    data class OpenSubScreen(val screen: SettingsSubScreen) : SettingsItemAction()
    object OpenContact : SettingsItemAction()
}

internal fun settingsItemAction(item: SearchableSetting): SettingsItemAction =
    if (item.subScreen == SettingsSubScreen.MAIN && item.title.contains("التواصل")) {
        SettingsItemAction.OpenContact
    } else {
        SettingsItemAction.OpenSubScreen(item.subScreen)
    }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToTasteProfile: () -> Unit = {},
    onNavigateToPremiumUpgrade: () -> Unit = {},
    onNavigateToAboutUs: () -> Unit = {},
    onNavigateToRules: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    // Batch 6 Priority 3 — guest upgrade entry point. Defaults to onLogout,
    // which already returns a guest to the login screen.
    onNavigateToRegister: () -> Unit = onLogout,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val reducedMotion by UserPreferencesManager.reducedMotion.collectAsState()
    val currentThemeMode by ThemeManager.themeMode.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val allBugReports by BugReportRepository.reports.collectAsState()
    val supabaseAuthUser by SupabaseServices.Auth.currentUser.collectAsState()
    val cloudAuthUser by CloudServices.Auth.currentUser.collectAsState()
    val supabaseUserId = remember(supabaseAuthUser, cloudAuthUser) {
        SupabaseServices.Auth.getCurrentUserId()
    }
    val authenticatedEmail = supabaseAuthUser?.email ?: cloudAuthUser?.email
    val accountPrefs = remember(supabaseAuthUser, cloudAuthUser, authenticatedEmail) {
        AccountScopedPreferences.forCurrentAccount(context)
    }
    val userBugReports = remember(
        allBugReports, supabaseAuthUser, cloudAuthUser, supabaseUserId, authenticatedEmail
    ) {
        BugReportRepository.filterReportsForOwner(
            source = allBugReports,
            localOwnerId = UserPreferencesManager.getEffectiveUserId(context),
            supabaseUserId = supabaseUserId,
            authenticatedEmail = authenticatedEmail
        )
    }

    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }
    var settingsSearchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = currentSubScreen != SettingsSubScreen.MAIN) {
        currentSubScreen = when (currentSubScreen) {
            SettingsSubScreen.CYBER_HUD_PREVIEW -> SettingsSubScreen.UX_DESIGN_STUDIO
            SettingsSubScreen.CRASH_LOGS -> SettingsSubScreen.COMMUNITY_SUPPORT
            else -> SettingsSubScreen.MAIN
        }
    }

    var savedDisplayName by remember {
        mutableStateOf(
            prefs.getString("user_display_name", null)
                ?: CloudServices.Auth.currentUser.value?.displayName
                ?: "مستخدم خادم المسلم"
        )
    }
    var avatarUriString by remember {
        mutableStateOf(
            accountPrefs?.getString("user_avatar_uri", null)
                ?: CloudServices.Auth.currentUser.value?.photoUrl?.toString()
                ?: ""
        )
    }
    var selectedPresetAvatar by remember {
        mutableIntStateOf(accountPrefs?.getInt("user_preset_avatar", 0) ?: 0)
    }
    var showAvatarPicker by remember { mutableStateOf(false) }

    LaunchedEffect(supabaseUserId) {
        val userId = supabaseUserId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val cloudAvatar = withContext(Dispatchers.IO) {
            SupabaseServices.Profiles.getUserProfile(userId)?.avatarUrl?.trim()
        }?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
        if (!cloudAvatar.isNullOrBlank()) {
            avatarUriString = cloudAvatar
            selectedPresetAvatar = -1
            accountPrefs?.edit()
                ?.putString("user_avatar_uri", cloudAvatar)
                ?.putInt("user_preset_avatar", -1)
                ?.apply()
        }
    }

    val storedEmail = prefs.getString("user_email", "") ?: ""
    val userEmail = CloudServices.Auth.currentUser.value?.email?.ifBlank { storedEmail } ?: storedEmail.ifBlank { "المستخدم الحالي" }
    val accountAuthState by SupabaseRepository.getInstance().authState.collectAsState()
    val isGuest = accountAuthState.requiresInteractiveLogin()
    val isLoggedInAccount = accountAuthState is AuthState.Authenticated
    val canShowLoginOrLogout = isGuest || isLoggedInAccount
    val verifiedRole by SupabaseServices.Auth.verifiedRole.collectAsState()
    val isAdmin = remember(accountAuthState, verifiedRole) {
        SupabaseServices.Auth.isAdminRole(verifiedRole) ||
            CloudServices.isOwnerAccount(SupabaseServices.Auth.getCurrentUserEmail())
    }
    val isRelative = prefs.getBoolean("is_relative_user", false)

    // Batch 6 Priority 3 — email verification state for the banner below.
    // Defaults to true (unknown = no banner) and is refreshed from the real
    // Auth user whenever a signed-in account is shown.
    var emailConfirmed by remember { mutableStateOf(prefs.getBoolean("email_confirmed", true)) }
    LaunchedEffect(isLoggedInAccount) {
        if (isLoggedInAccount) {
            emailConfirmed = SupabaseServices.Auth.refreshEmailConfirmationState() ?: true
        }
    }

    val isDeveloperAccount = remember(accountAuthState, verifiedRole) {
        AdminGuard.isDashboardAccessAllowed(context)
    }
    val canViewCrashTelemetry = remember(supabaseAuthUser) { AdminGuard.isCrashTelemetryAccessAllowed() }
    val isSyncing by UserPreferencesManager.isSyncing.collectAsState()

    val allSearchableItems = remember(isLoggedInAccount, isGuest, isDeveloperAccount, userBugReports, theme) {
        val unreadReportsCount = userBugReports.count { !it.isReadByUser }
        listOf(
            SearchableSetting("الاستوديو الروحاني والمصحف الشريف", "تخصيص القراءة، الأذان، مواقيت الصلاة، والأذكار", Icons.Default.MenuBook, SettingsSubScreen.SPIRITUAL_STUDIO, listOf("قرآن", "صلاة", "أذان", "أذكار"), badge = "زاد إيماني", accentColor = theme.success, group = "العبادة والتنبيهات"),
            SearchableSetting("إعدادات الإشعارات والتنبيهات الإيمانية", "أوقات الأذان، تنبيه السحر، الفجر، الحديث اليومي", Icons.Default.NotificationsActive, SettingsSubScreen.NOTIFICATION_SETTINGS, listOf("تنبيه", "إشعار"), badge = "التنبيهات", accentColor = theme.warning, group = "العبادة والتنبيهات"),
            SearchableSetting("مركز الأذونات وحماية النظام", "صلاحيات الميكروفون، الكاميرا، وعمل التنبيهات بالخلفية", Icons.Default.Shield, SettingsSubScreen.PERMISSIONS_HUB, listOf("أمان", "صلاحية"), badge = "الحماية", accentColor = theme.primary, group = "العبادة والتنبيهات"),
            SearchableSetting("المظهر والواجهة واللغة", "الوضع الفاتح والداكن، الألوان، الخط، ولغة التطبيق", Icons.Default.Palette, SettingsSubScreen.UX_DESIGN_STUDIO, listOf("ديزاين", "ثيم", "لغة", "خط", "مظهر"), badge = "المظهر", accentColor = theme.primary, group = "المظهر والتجربة"),
            SearchableSetting("المؤثرات الصوتية والصدى", "التحكم بالأصوات التفاعلية، مستوى الصوت، والاهتزاز", Icons.Default.GraphicEq, SettingsSubScreen.SOUND_STUDIO, listOf("صوت", "اهتزاز", "نقر"), badge = "الصوت", accentColor = theme.accent, group = "المظهر والتجربة"),
            SearchableSetting("إعدادات سهولة الوصول", "التباين العالي، تقليل الحركة، وحجم خط التطبيق", Icons.Default.AccessibilityNew, SettingsSubScreen.ACCESSIBILITY, listOf("سهولة الوصول", "تباين", "خط", "حركة"), badge = "الوصول", accentColor = theme.success, group = "المظهر والتجربة"),
            SearchableSetting("مركز الإنتاج والذكاء الاحترافي", "هوية الذكاء، العلامة المائية، وجودة التصدير", Icons.Default.Psychology, SettingsSubScreen.PRO_PRODUCTION_HUB, listOf("ذكاء", "فيديو", "إنتاج", "علامة مائية"), badge = "الإنتاج", accentColor = Color(0xFF5856D6), group = "الإنتاج والإبداع"),
            SearchableSetting("إعدادات مشغل الفيديو الموحد", "سرعة التشغيل، ملاءمة الصورة، وأزرار التحكم", Icons.Default.PlayCircle, SettingsSubScreen.VIDEO_PLAYER_SETTINGS, listOf("فيديو", "مشغل", "سرعة"), badge = "المشغل", accentColor = theme.primary, group = "الإنتاج والإبداع"),
            SearchableSetting("النظام والبيانات والمجتمع", "النسخ الاحتياطي، الذاكرة، السياسات، والروابط الرسمية", Icons.Default.SettingsSuggest, SettingsSubScreen.COMMUNITY_SUPPORT, listOf("كاش", "نظام", "بيانات", "احتياطي"), badge = "البيانات", accentColor = Color(0xFF5856D6), group = "البيانات والحساب"),
            SearchableSetting("إعدادات كاميرا البلاغات", "التحكم بزر الكاميرا العائم المستخدم لإرسال البلاغات", Icons.Default.CameraAlt, SettingsSubScreen.BUG_CAMERA_CONTROL, listOf("كاميرا", "بلاغ", "خطأ"), badge = "البلاغات", accentColor = theme.primary, group = "التواصل والمساندة")
        ).let { base ->
            val withDeveloperSettings = if (isDeveloperAccount) {
                base + SearchableSetting("أدوات المطور وتحديثات xdelta", "فحص الإصدارات والتشخيص المتقدم لمستودع خادم المسلم", Icons.Default.CloudSync, SettingsSubScreen.GITHUB_XDELTA_HUB, listOf("تحديث", "جيتهاب", "xdelta", "مطور"), badge = "للمطور", accentColor = theme.destructive, group = "أدوات المطور")
            } else base
            if (isLoggedInAccount) withDeveloperSettings + SearchableSetting(
                "مركز التواصل مع المطور", "متابعة بلاغاتك والتواصل مع المطور داخل محادثة موحدة", Icons.Default.SupportAgent, SettingsSubScreen.MAIN, listOf("دعم", "دردشة", "مساعدة"), badge = if (unreadReportsCount > 0) "$unreadReportsCount ردود جديدة" else "التواصل", accentColor = if (unreadReportsCount > 0) theme.warning else theme.primary, badgeColor = if (unreadReportsCount > 0) theme.warning else null, group = "التواصل والمساندة")
            else withDeveloperSettings
        }
    }

    val filteredSettings = remember(settingsSearchQuery, allSearchableItems) {
        if (settingsSearchQuery.isBlank()) allSearchableItems
        else {
            allSearchableItems.filter { item ->
                item.title.contains(settingsSearchQuery, ignoreCase = true) ||
                    item.subtitle.contains(settingsSearchQuery, ignoreCase = true) ||
                    item.tags.any { it.contains(settingsSearchQuery, ignoreCase = true) }
            }
        }
    }

    var accountsList by remember { mutableStateOf(SocialAccountManager.getAccounts(context)) }
    var linkDialogState by remember { mutableStateOf<String?>(null) }
    var isLinking by remember { mutableStateOf(false) }

    val selectedPlatform = remember(linkDialogState, accountsList) {
        accountsList.find { it.id == linkDialogState }
    }
    var customHandle by remember(selectedPlatform) {
        mutableStateOf(selectedPlatform?.handle ?: "")
    }
    var customToken by remember(selectedPlatform) {
        mutableStateOf(if (selectedPlatform != null) SocialAccountManager.getAccessToken(context, selectedPlatform.id) else "")
    }

    var updateState by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var updateDoneMessage by remember { mutableStateOf("") }
    var updateActiveKey by remember { mutableStateOf<String?>(null) }
    var lastConsumedResult by remember { mutableStateOf<Triple<String, Boolean, String>?>(null) }
    var cacheSizeBytes by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        cacheSizeBytes = withContext(Dispatchers.IO) {
            runCatching { cacheBytes(context) }.getOrNull()
        }
    }

    LaunchedEffect(Unit) {
        UpdateManager.downloadProgress.collect { p ->
            if (p != null && updateState == "downloading" && updateActiveKey != null) {
                updateProgress = p.percent
            }
        }
    }
    LaunchedEffect(Unit) {
        UpdateManager.downloadResult.collect { r ->
            if (r != null && r != lastConsumedResult && r.first == updateActiveKey && updateState == "downloading") {
                lastConsumedResult = r
                updateDoneMessage = r.third
                updateState = when {
                    r.second -> "done"
                    r.third.contains("أُلغي") -> "found"
                    else -> "error"
                }
            }
        }
    }

    fun beginServiceDownload(info: UpdateManager.UpdateInfo, forceFull: Boolean) {
        updateActiveKey = "${info.versionName}|${info.versionCode}"
        lastConsumedResult = null
        updateState = "downloading"
        updateProgress = 0
        UpdateDownloadService.start(context, info, forceFull)
    }

    LaunchedEffect(Unit) {
        if (updateState == null) {
            updateState = "checking..."
            runCatching {
                val info = VersionManager.checkRemoteVersion(context)
                if (info != null) {
                    updateInfo = info
                    updateState = "found"
                    showUpdateDialog = true
                    prefs.edit().putString("last_release_notes", info.releaseNotes).apply()
                } else {
                    updateState = null
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onFailure {
                updateState = null
            }
        }
    }

    val isDevMode = isDeveloperAccount

    // Dialog: Link social account
    if (linkDialogState != null && selectedPlatform != null) {
        AlertDialog(
            onDismissRequest = { if (!isLinking) linkDialogState = null },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (selectedPlatform.id) {
                            "youtube" -> Icons.Default.OndemandVideo
                            "tiktok" -> Icons.Default.MusicVideo
                            else -> Icons.Default.CameraAlt
                        },
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        Translator.tr("إعدادات ربط ") + selectedPlatform.name,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        Translator.tr("اربط حسابك لإدارة النشر والتصدير إلى ") + selectedPlatform.name,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = customHandle,
                        onValueChange = { customHandle = it },
                        label = { Text(Translator.tr("اسم المستخدم أو المعرف (Handle)"), fontSize = 12.sp) },
                        placeholder = { Text("@username") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customToken,
                        onValueChange = { customToken = it },
                        label = { Text(Translator.tr("رمز الوصول (Access Token / API Key)"), fontSize = 12.sp) },
                        placeholder = { Text(Translator.tr("اختياري — للنشر المباشر")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isLinking = true
                        coroutineScope.launch {
                            SocialAccountManager.toggleConnection(
                                context,
                                selectedPlatform.id,
                                isConnected = customHandle.isNotBlank(),
                                handle = customHandle.trim(),
                                apiToken = customToken.trim()
                            )
                            accountsList = SocialAccountManager.getAccounts(context)
                            isLinking = false
                            linkDialogState = null
                            Toast.makeText(context, "تم حفظ بيانات الحساب بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    enabled = !isLinking,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(Translator.tr("حفظ"), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { linkDialogState = null }, enabled = !isLinking) {
                    Text(Translator.tr("إلغاء"), color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        )
    }

    AnimatedContent(
        targetState = currentSubScreen,
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (targetState != SettingsSubScreen.MAIN) {
                slideInHorizontally { width -> -width } + fadeIn() togetherWith slideOutHorizontally { width -> width } + fadeOut()
            } else {
                slideInHorizontally { width -> width } + fadeIn() togetherWith slideOutHorizontally { width -> -width } + fadeOut()
            }
        },
        label = "SettingsSubScreenAnimation"
    ) { subScreen ->
        when (subScreen) {
            SettingsSubScreen.MAIN -> {
                Scaffold(
                    topBar = {
                        AppleTopBar(
                            title = Translator.tr("الإعدادات"),
                            onBack = onBack,
                            largeTitle = true,
                            actions = {
                                if (canShowLoginOrLogout) {
                                    IconButton(onClick = onLogout, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            if (isGuest) Icons.Default.Login else Icons.Default.Logout,
                                            contentDescription = if (isGuest) "تسجيل الدخول" else "تسجيل الخروج",
                                            tint = if (isGuest) theme.primary else theme.destructive
                                        )
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = bottomBar,
                    containerColor = theme.background
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileSettingsCard(
                            savedDisplayName = savedDisplayName,
                            userEmail = if (isGuest) "guest@qabas.studio" else userEmail,
                            avatarUriString = avatarUriString,
                            selectedPresetAvatar = selectedPresetAvatar,
                            isAdmin = isAdmin,
                            isGuest = isGuest,
                            isRelative = isRelative,
                            onUpgrade = onNavigateToPremiumUpgrade,
                            onAvatarClick = { showAvatarPicker = true },
                            context = context
                        )

                        // Batch 6 Priority 3 — guest upgrade entry point.
                        // There is no guest banner on the home screen, so
                        // Settings is where a guest is offered to keep their
                        // local data by creating an account. The flag makes
                        // the registration/login flow migrate guest data to
                        // the new account id (see GuestUpgradeManager).
                        if (isGuest) {
                            AppleCard(
                                onClick = {
                                    GuestUpgradeManager.markGuestUpgradePending(context)
                                    onNavigateToRegister()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "أنشئ حساباً للاحتفاظ ببياناتك",
                                                color = theme.textPrimary,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "إشاراتك ومواضع قراءتك وإعداداتك ستنتقل إلى حسابك الجديد",
                                                color = theme.textSecondary,
                                                fontFamily = NotoSansFont,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = theme.textSecondary)
                                }
                            }
                        }

                        // Batch 6 Priority 3 — honest email-verification
                        // banner for signed-in users whose address is still
                        // unconfirmed (local use continues; cloud sync is
                        // blocked until confirmation).
                        if (isLoggedInAccount && !emailConfirmed) {
                            EmailVerificationBanner(email = userEmail)
                        }

                        // Batch 6 Priority 3 — real account deletion, for
                        // signed-in users (guests have no account to delete).
                        if (isLoggedInAccount) {
                            AccountDeletionCard()
                        }

                        SettingsSearchBar(
                            query = settingsSearchQuery,
                            onQueryChange = { settingsSearchQuery = it }
                        )

                        if (settingsSearchQuery.isBlank()) {
                            // Quick Light/Dark Mode Switch right on main Settings (Apple HIG)
                            AppleGroupedSection(header = "المظهر السريع") {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    AppleSegmentedControl(
                                        items = listOf(
                                            AppThemeMode.SYSTEM to "تلقائي",
                                            AppThemeMode.LIGHT to "الوضع الفاتح",
                                            AppThemeMode.DARK to "الوضع الداكن"
                                        ),
                                        selectedItem = currentThemeMode,
                                        onSelect = { mode -> ThemeManager.setThemeMode(context, mode) },
                                        modifier = Modifier.testTag("settings_theme_mode_toggle")
                                    )
                                }
                            }

                            SettingsQuickActions(
                                onClearCache = {
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) { clearTemporaryCache(context) }
                                        cacheSizeBytes = withContext(Dispatchers.IO) { cacheBytes(context) }
                                        Toast.makeText(context, "تم تنظيف الملفات المؤقتة بنجاح", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSyncCloud = {
                                    coroutineScope.launch {
                                        val synced = runCatching { UserPreferencesManager.syncToSupabase(context) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)
                                        Toast.makeText(
                                            context,
                                            if (synced) "تمت مزامنة إعداداتك عبر Supabase بنجاح" else "تعذرت المزامنة السحابية. تحقق من الاتصال وتسجيل الدخول.",
                                            if (synced) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                onCheckUpdate = {
                                    if (updateState != "checking..." && updateState != "downloading") {
                                        updateState = "checking..."
                                        coroutineScope.launch {
                                            // فحص مميِّز (جولة 39، بنمط الجولة 24 في شاشة «عن التطبيق»):
                                            // كان null من checkRemoteVersion يعني «لا تحديث» و«تعذّر
                                            // الوصول» معاً، فكانت الشاشة تدّعي «أحدث نسخة» دون اتصال.
                                            runCatching { VersionManager.checkRemoteVersionDetailed(context, force = true) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                                .onSuccess { result ->
                                                    when (result) {
                                                        is UpdateManager.UpdateCheckResult.Available -> {
                                                            val info = result.info
                                                            updateInfo = info
                                                            updateState = "found"
                                                            showUpdateDialog = true
                                                            prefs.edit().putString("last_release_notes", info.releaseNotes).apply()
                                                            Toast.makeText(context, "تم العثور على تحديث جديد", Toast.LENGTH_SHORT).show()
                                                        }
                                                        UpdateManager.UpdateCheckResult.UpToDate -> {
                                                            updateState = null
                                                            Toast.makeText(context, "أنت تستخدم أحدث نسخة بالفعل", Toast.LENGTH_SHORT).show()
                                                        }
                                                        UpdateManager.UpdateCheckResult.Failed,
                                                        UpdateManager.UpdateCheckResult.Skipped -> {
                                                            // مع force = true لا يحدث Skipped عملياً (التخطي
                                                            // فقط عند !force) — يُعامل كفشل دفاعاً.
                                                            updateState = null
                                                            Toast.makeText(context, "تعذّر الوصول إلى خادم التحديثات، تحقق من اتصال الإنترنت وحاول لاحقاً.", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                                .onFailure {
                                                    updateState = null
                                                    Toast.makeText(context, "تعذر فحص التحديثات. تحقق من الاتصال.", Toast.LENGTH_LONG).show()
                                                }
                                        }
                                    }
                                },
                                isSyncing = isSyncing,
                                isCheckingUpdate = updateState == "checking...",
                                isDownloadingUpdate = updateState == "downloading"
                            )
                        }

                        if (settingsSearchQuery.isNotBlank()) {
                            SettingsGroupHeader("نتائج البحث (${filteredSettings.size})")
                            filteredSettings.forEach { item ->
                                SettingsNavigationCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    icon = item.icon,
                                    badge = item.badge ?: "",
                                    badgeColor = item.badgeColor ?: item.accentColor,
                                    accentColor = item.accentColor,
                                    onClick = {
                                        when (val action = settingsItemAction(item)) {
                                            SettingsItemAction.OpenContact -> {
                                                AppSoundManager.playClick()
                                                AppNavigator.navigateTo(AppState.REQUEST_CHAT)
                                            }
                                            is SettingsItemAction.OpenSubScreen -> {
                                                currentSubScreen = action.screen
                                            }
                                        }
                                    }
                                )
                            }
                            if (filteredSettings.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    Text("لم نجد إعداداً بهذا الاسم", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 14.sp)
                                }
                            }
                        } else {
                            allSearchableItems.groupBy { it.group }.forEach { (groupTitle, groupItems) ->
                                SettingsGroupHeader(groupTitle)
                                groupItems.forEach { item ->
                                    SettingsNavigationCard(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        icon = item.icon,
                                        badge = item.badge ?: "",
                                        badgeColor = item.badgeColor ?: item.accentColor,
                                        accentColor = item.accentColor,
                                        onClick = {
                                            when (val action = settingsItemAction(item)) {
                                                SettingsItemAction.OpenContact -> {
                                                    AppSoundManager.playClick()
                                                    AppNavigator.navigateTo(AppState.REQUEST_CHAT)
                                                }
                                                is SettingsItemAction.OpenSubScreen -> {
                                                    currentSubScreen = action.screen
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (canShowLoginOrLogout) {
                            AppleSecondaryButton(
                                text = if (isGuest) Translator.tr("تسجيل الدخول / إنشاء حساب") else Translator.tr("تسجيل الخروج من الحساب"),
                                icon = if (isGuest) Icons.Default.Login else Icons.Default.Logout,
                                tint = if (isGuest) theme.primary else theme.destructive,
                                onClick = onLogout
                            )
                        } else {
                            Text(
                                text = if (accountAuthState is AuthState.Loading)
                                    "جارٍ التحقق من حالة الحساب…"
                                else "تعذر التحقق من حالة الحساب مؤقتًا؛ لا حاجة لإعادة تسجيل الدخول.",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            SettingsSubScreen.UX_DESIGN_STUDIO -> {
                SubScreenScaffold(
                    title = "المظهر والواجهة واللغة",
                    icon = Icons.Default.Palette,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                ) {
                    SettingsActionCard(
                        title = "المظهر (فاتح / داكن) والألوان",
                        subtitle = "التبديل بين الوضع الفاتح والداكن واختيار مجموعة الألوان",
                        icon = Icons.Default.Palette,
                        accentColor = theme.primary,
                        onClick = { currentSubScreen = SettingsSubScreen.CYBER_HUD_PREVIEW }
                    )
                    SettingsActionCard(
                        title = "الخط العام للواجهة",
                        subtitle = "غيّر خط النصوص في التطبيق مع معاينة مباشرة",
                        icon = Icons.Default.TextFields,
                        accentColor = theme.accent,
                        content = { FontSelectionSection() }
                    )
                    SettingsActionCard(
                        title = "لغة التطبيق",
                        subtitle = "التبديل الفوري بين العربية والإنجليزية",
                        icon = Icons.Default.Language,
                        accentColor = theme.warning,
                        content = { LanguageSelectionSection() }
                    )
                }
            }

            SettingsSubScreen.SPIRITUAL_STUDIO -> {
                SubScreenScaffold(
                    title = "الاستوديو الروحاني والمصحف",
                    icon = Icons.Default.MenuBook,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                ) {
                    SettingsActionCard(
                        title = "تخصيص القراءة والمصحف",
                        subtitle = "حجم الخط، نوع الرسم، وأنماط الورق",
                        icon = Icons.Default.AutoStories,
                        accentColor = theme.success,
                        content = { ReadingCustomizationSection() }
                    )
                    SettingsActionCard(
                        title = "الأذان ومواقيت الصلاة",
                        subtitle = "حساب المواقيت، اختيار المؤذن، وخلفية الأذان",
                        icon = Icons.Default.Mosque,
                        accentColor = theme.warning,
                        content = { SpiritualPreferencesSection() }
                    )
                }
            }

            SettingsSubScreen.PRO_PRODUCTION_HUB -> {
                SubScreenScaffold(
                    title = "مركز الإنتاج والذكاء الاحترافي",
                    icon = Icons.Default.Psychology,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                ) {
                    SettingsActionCard(
                        title = "هوية الذكاء الاصطناعي",
                        subtitle = "تنسيق نبرة وأسلوب المخرج الذكي",
                        icon = Icons.Default.AutoAwesome,
                        accentColor = Color(0xFF5856D6),
                        content = { AiIdentitySection(onNavigateToTasteProfile) }
                    )
                    SettingsActionCard(
                        title = "العلامة المائية وجودة التصدير",
                        subtitle = "تخصيص الشعار والأبعاد الافتراضية",
                        icon = Icons.Default.VideoSettings,
                        accentColor = theme.primary,
                        content = { StudioWatermarkPreferencesSection() }
                    )
                }
            }

            SettingsSubScreen.COMMUNITY_SUPPORT -> {
                SubScreenScaffold(
                    title = "النظام والبيانات والمجتمع",
                    icon = Icons.Default.SettingsSuggest,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                ) {
                    SettingsGroupHeader(Translator.tr("البيانات والنسخ الاحتياطي"))
                    SettingsActionCard(
                        title = "المزامنة السحابية وإدارة الذاكرة",
                        subtitle = "تصدير الإعدادات، استعادة البيانات، وتفريغ الكاش",
                        icon = Icons.Default.CloudSync,
                        accentColor = Color(0xFF5856D6),
                        content = {
                            StorageAndBackupSection(
                                cacheSizeBytes = cacheSizeBytes,
                                isDeveloper = isAdmin || isDevMode,
                                onClearCache = {
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) { clearTemporaryCache(context) }
                                        cacheSizeBytes = withContext(Dispatchers.IO) { cacheBytes(context) }
                                        Toast.makeText(context, "تم تنظيف الملفات المؤقتة بنجاح", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    )

                    SettingsGroupHeader(Translator.tr("المجتمع والتواصل"))
                    SettingsActionCard(
                        title = "ملتقى خادم المسلم والمجتمعات الرسمية",
                        subtitle = "القنوات الرسمية ومجتمع المبدعين",
                        icon = Icons.Default.Hub,
                        accentColor = theme.primary,
                        content = { CommunityAndLinksSection() }
                    )
                    CommunityReviewsCard()

                    SettingsGroupHeader(Translator.tr("المنظومة والسياسات"))
                    SettingsActionCard(
                        title = "منظومة خادم المسلم والضوابط الشرعية",
                        subtitle = "الرؤية، الإصدار، والاعتماد الشرعي",
                        icon = Icons.Default.VerifiedUser,
                        accentColor = theme.primary,
                        onClick = onNavigateToAboutUs
                    )
                    SettingsActionCard(
                        title = "سجل التحديثات التفصيلي",
                        subtitle = "تاريخ تطور منظومة خادم المسلم والمزايا الجديدة",
                        icon = Icons.Default.History,
                        accentColor = theme.warning,
                        onClick = onNavigateToHistory
                    )
                    SettingsActionCard(
                        title = "القواعد والسياسات الموحدة",
                        subtitle = "شروط الاستخدام، سياسة الخصوصية، وقواعد المجتمع",
                        icon = Icons.Default.Gavel,
                        accentColor = theme.warning,
                        onClick = onNavigateToRules
                    )

                    SettingsGroupHeader(Translator.tr("الدعم والتشخيص"))
                    if (isLoggedInAccount) {
                        SettingsActionCard(
                            title = "سجل بلاغاتي واقتراحاتي",
                            subtitle = "متابعة حالة الأعطال والاقتراحات وردود الدعم",
                            icon = Icons.Default.ListAlt,
                            accentColor = theme.warning,
                            onClick = { AppNavigator.navigateTo(AppState.REQUEST_CHAT) }
                        )
                    }
                    if (canViewCrashTelemetry) {
                        SettingsActionCard(
                            title = "سجل الانهيارات والأعطال (Crash Guard)",
                            subtitle = "تشخيص الأعطال الحية وتصدير التقارير",
                            icon = Icons.Default.BugReport,
                            accentColor = theme.destructive,
                            onClick = { currentSubScreen = SettingsSubScreen.CRASH_LOGS }
                        )
                    }
                }
            }

            SettingsSubScreen.CYBER_HUD_PREVIEW -> {
                ThemeDesignStudioScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.UX_DESIGN_STUDIO }
                )
            }

            SettingsSubScreen.SOUND_STUDIO -> {
                SoundEffectsSubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }

            SettingsSubScreen.VIDEO_PLAYER_SETTINGS -> {
                UnifiedVideoPlayerSettingsScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                )
            }

            SettingsSubScreen.PERMISSIONS_HUB -> {
                AppPermissionsHubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }

            SettingsSubScreen.CRASH_LOGS -> {
                if (canViewCrashTelemetry) {
                    CrashLogsHubScreen(
                        onBack = { currentSubScreen = SettingsSubScreen.COMMUNITY_SUPPORT }
                    )
                } else {
                    currentSubScreen = SettingsSubScreen.COMMUNITY_SUPPORT
                }
            }

            SettingsSubScreen.NOTIFICATION_SETTINGS -> {
                NotificationSettingsScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }

            SettingsSubScreen.GITHUB_XDELTA_HUB -> {
                if (!isDeveloperAccount) {
                    currentSubScreen = SettingsSubScreen.MAIN
                } else {
                    GithubXdeltaHubScreen(
                        onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                    )
                }
            }

            SettingsSubScreen.BUG_CAMERA_CONTROL -> {
                BugCameraSettingsScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    onOpenMyReports = { AppNavigator.navigateTo(AppState.REQUEST_CHAT) }
                )
            }

            SettingsSubScreen.ACCESSIBILITY -> {
                SubScreenScaffold(
                    title = "إعدادات سهولة الوصول",
                    icon = Icons.Default.AccessibilityNew,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                ) {
                    AccessibilitySettingsSection()
                }
            }
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { if (updateState != "downloading") showUpdateDialog = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = { Text("تحديث جديد متاح: ${info.versionName}", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (updateState) {
                            "downloading" -> "جاري تنزيل التحديث… $updateProgress٪"
                            "done" -> updateDoneMessage.ifBlank { "اكتمل تنزيل التحديث. افتح إشعار النظام للتثبيت." }
                            "error" -> updateDoneMessage.ifBlank { "تعذر تنزيل التحديث." }
                            else -> info.releaseNotes.ifBlank { "يتضمن هذا الإصدار تحسينات في الأداء والاستقرار." }
                        },
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp
                    )
                    if (updateState == "downloading") {
                        LinearProgressIndicator(progress = { updateProgress / 100f }, modifier = Modifier.fillMaxWidth(), color = theme.primary)
                    }
                }
            },
            confirmButton = {
                if (updateState != "downloading" && updateState != "done") {
                    Button(
                        onClick = { beginServiceDownload(info, forceFull = info.isForce) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("تنزيل التحديث", fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (updateState != "downloading") showUpdateDialog = false }) {
                    Text(if (updateState == "downloading") "التنزيل مستمر بالخلفية" else "إغلاق", color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        )
    }

    if (showAvatarPicker) {
        val avatarOptions = listOf(
            0 to (Icons.Default.Person to "الشخصية الافتراضية"),
            1 to (Icons.Default.AutoAwesome to "إبداع خادم المسلم"),
            2 to (Icons.Default.MovieFilter to "صانع المحتوى"),
            3 to (Icons.Default.MenuBook to "القارئ"),
            4 to (Icons.Default.Psychology to "الذكاء")
        )
        AlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = { Text("اختيار الصورة الرمزية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر صورة رمزية للملف الشخصي.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    avatarOptions.forEach { (id, option) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedPresetAvatar == id) theme.primary.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable {
                                    accountPrefs?.edit()?.putInt("user_preset_avatar", id)?.remove("user_avatar_uri")?.apply()
                                    avatarUriString = ""
                                    selectedPresetAvatar = id
                                    showAvatarPicker = false
                                    SupabaseServices.Auth.getCurrentUserId()?.takeIf { it.isNotBlank() }?.let { userId ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            SupabaseServices.Profiles.syncUserProfile(
                                                userId = userId,
                                                displayName = savedDisplayName,
                                                email = userEmail,
                                                avatarUrl = ""
                                            )
                                        }
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(option.first, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(option.second, color = theme.textPrimary, fontFamily = CairoFont, fontSize = 13.5.sp)
                            if (selectedPresetAvatar == id) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Check, contentDescription = null, tint = theme.success, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarPicker = false }) {
                    Text("إغلاق", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
