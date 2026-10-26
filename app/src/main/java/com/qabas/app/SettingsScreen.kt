package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush as UiBrush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشات الإعدادات الفرعية المستقلة (Dedicated Sub-Screens Architecture)
 */
enum class SettingsSubScreen {
    MAIN,
    UX_DESIGN_STUDIO,     // Merge: Home Style, Animations, Sound Effects, Language
    SOUND_STUDIO,         // Dedicated Sound Effects Control Studio
    SPIRITUAL_STUDIO,      // Merge: Reading Customization, Spiritual Preferences
    PRO_PRODUCTION_HUB,    // Merge: AI Identity, Watermark, Quality
    COMMUNITY_SUPPORT,     // Merge: About, Official Channels, Social, Support
    CYBER_HUD_PREVIEW,      // Keep for previewing design
    PERMISSIONS_HUB,       // Cybernetic Permissions & System Protection HUD
    CRASH_LOGS,            // Spatial Crash Guard & Diagnostics HUD
    NOTIFICATION_SETTINGS, // Full Notification & Reminders Settings HUD
    GITHUB_XDELTA_HUB,     // Direct GitHub Connection & xdelta Updates HUD
    BUG_CAMERA_CONTROL     // Spatial Bug Camera Button Control HUD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToTasteProfile: () -> Unit = {},
    onNavigateToPremiumUpgrade: () -> Unit = {},
    onNavigateToAboutUs: () -> Unit = {},
    onNavigateToRules: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    // حالة الشاشة الفرعية الحالية
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }

    // معالجة الرجوع بنظام BackHandler
    BackHandler(enabled = currentSubScreen != SettingsSubScreen.MAIN) {
        currentSubScreen = when (currentSubScreen) {
            SettingsSubScreen.CYBER_HUD_PREVIEW -> SettingsSubScreen.UX_DESIGN_STUDIO
            SettingsSubScreen.SOUND_STUDIO -> SettingsSubScreen.MAIN
            SettingsSubScreen.PERMISSIONS_HUB -> SettingsSubScreen.MAIN
            SettingsSubScreen.CRASH_LOGS -> SettingsSubScreen.COMMUNITY_SUPPORT
            SettingsSubScreen.NOTIFICATION_SETTINGS -> SettingsSubScreen.MAIN
            SettingsSubScreen.GITHUB_XDELTA_HUB -> SettingsSubScreen.MAIN
            else -> SettingsSubScreen.MAIN
        }
    }

    // بيانات المستخدم والملف الشخصي
    var savedDisplayName by remember {
        mutableStateOf(
            prefs.getString("user_display_name", null)
                ?: CloudServices.Auth.currentUser.value?.displayName
                ?: "مستخدم قبس"
        )
    }
    var avatarUriString by remember {
        mutableStateOf(
            prefs.getString("user_avatar_uri", null)
                ?: CloudServices.Auth.currentUser.value?.photoUrl?.toString()
                ?: ""
        )
    }
    var selectedPresetAvatar by remember {
        mutableIntStateOf(prefs.getInt("user_preset_avatar", 0))
    }

    val userEmail = CloudServices.Auth.currentUser.value?.email ?: "المستخدم الحالي"
    val isAdmin = prefs.getBoolean("is_admin", false) || CloudServices.isOwnerAccount(userEmail)
    val isRelative = prefs.getBoolean("is_relative_user", false)
    val isGuest = prefs.getBoolean("is_guest", false)
    val isLoggedInAccount = !isGuest && (prefs.getBoolean("is_logged_in", false) || CloudServices.Auth.currentUser.value != null || prefs.getString("user_email", "").orEmpty().isNotBlank())

    var autoPublishing by remember {
        mutableStateOf(prefs.getBoolean("auto_publish", false))
    }
    var defaultQuality by remember {
        mutableStateOf(prefs.getString("default_export_quality", "1080p") ?: "1080p")
    }

    // الحسابات وقنوات التواصل
    var accountsList by remember { mutableStateOf(SocialAccountManager.getAccounts(context)) }
    var linkDialogState by remember { mutableStateOf<String?>(null) } // "youtube", "tiktok", "instagram"
    var isLinking by remember { mutableStateOf(false) }
    var linkVerifyMsg by remember { mutableStateOf<String?>(null) }

    val selectedPlatform = remember(linkDialogState, accountsList) {
        accountsList.find { it.id == linkDialogState }
    }
    var customHandle by remember(selectedPlatform) {
        mutableStateOf(selectedPlatform?.handle ?: "")
    }
    var customToken by remember(selectedPlatform) {
        mutableStateOf(if (selectedPlatform != null) SocialAccountManager.getAccessToken(context, selectedPlatform.id) else "")
    }

    // التحديثات والنظام
    var showAboutAppDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var updateProgress by remember { mutableStateOf(0) }
    var updateDownloadedBytes by remember { mutableStateOf(0L) }
    var updateTotalBytes by remember { mutableStateOf(0L) }
    var updateSpeedBps by remember { mutableStateOf(0L) }
    var updatePhase by remember { mutableStateOf<UpdateManager.Phase?>(null) }
    var updateDoneMessage by remember { mutableStateOf("") }
    var updateActiveKey by remember { mutableStateOf<String?>(null) }
    var lastConsumedResult by remember { mutableStateOf<Triple<String, Boolean, String>?>(null) }
    var cacheSizeBytes by remember { mutableStateOf<Long?>(null) }
    var showUserReportsDialog by remember { mutableStateOf(false) }

    // حساب حجم المؤقتات
    LaunchedEffect(Unit) {
        cacheSizeBytes = withContext(Dispatchers.IO) {
            runCatching {
                var total = 0L
                context.cacheDir.walkTopDown().forEach { f -> if (f.isFile) total += f.length() }
                total
            }.getOrNull()
        }
    }

    // خدمة تنزيل التحديثات في الخلفية
    LaunchedEffect(Unit) {
        UpdateManager.downloadProgress.collect { p ->
            if (p != null && updateState == "downloading" && updateActiveKey != null) {
                updateProgress = p.percent
                updateDownloadedBytes = p.bytesDownloaded
                updateTotalBytes = p.totalBytes
                updateSpeedBps = p.speedBytesPerSec
                updatePhase = p.phase
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
        updateDownloadedBytes = 0L
        updateTotalBytes = 0L
        updateSpeedBps = 0L
        updatePhase = null
        UpdateDownloadService.start(context, info, forceFull)
    }

    // فحص التحديثات
    LaunchedEffect(Unit) {
        if (updateState == null) {
            updateState = "checking..."
            runCatching {
                val info = VersionManager.checkRemoteVersion(context)
                if (info != null) {
                    updateInfo = info
                    updateState = "found"
                    prefs.edit().putString("last_release_notes", info.releaseNotes).apply()
                } else {
                    updateState = null
                }
            }.onFailure {
                updateState = null
            }
        }
    }

    // Developer Mode
    var devTapCount by remember { mutableIntStateOf(0) }
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var passphraseInput by remember { mutableStateOf("") }
    var passphraseError by remember { mutableStateOf(false) }
    var isDevMode by remember {
        mutableStateOf(prefs.getBoolean("is_developer", false))
    }

    // الحسابات الرسمية
    val canManageChannels = isAdmin || isDevMode
    var officialChannels by remember { mutableStateOf(SocialAccountManager.getOfficialChannels(context)) }
    fun refreshChannels() {
        officialChannels = SocialAccountManager.getOfficialChannels(context)
    }
    var channelDraftId by remember { mutableStateOf<String?>(null) }
    var chPlatform by remember { mutableStateOf("") }
    var chDisplay by remember { mutableStateOf("") }
    var chHandle by remember { mutableStateOf("") }
    var chUrl by remember { mutableStateOf("") }
    var chDesc by remember { mutableStateOf("") }
    var chUrlError by remember { mutableStateOf(false) }
    fun openChannelEditor(c: OfficialChannelInfo?) {
        channelDraftId = c?.id ?: ""
        chPlatform = c?.platformName ?: ""
        chDisplay = c?.displayName ?: ""
        chHandle = c?.handle ?: ""
        chUrl = c?.url ?: ""
        chDesc = c?.description ?: ""
        chUrlError = false
    }

    // حوار ربط حساب تواصل
    if (linkDialogState != null && selectedPlatform != null) {
        AlertDialog(
            onDismissRequest = { if (!isLinking) linkDialogState = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (selectedPlatform.id) {
                            "youtube" -> Icons.Default.OndemandVideo
                            "tiktok" -> Icons.Default.MusicVideo
                            else -> Icons.Default.CameraAlt
                        },
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        Translator.tr("إعدادات ربط ") + selectedPlatform.name,
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        Translator.tr("اربط حسابك لنشر المقاطع وتصديرها مباشرة إلى ") + selectedPlatform.name,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = customHandle,
                        onValueChange = { customHandle = it },
                        label = { Text(Translator.tr("اسم المستخدم أو المعرف (Handle)"), fontSize = 12.sp) },
                        placeholder = { Text("@username", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customToken,
                        onValueChange = { customToken = it },
                        label = { Text(Translator.tr("رمز الوصول (Access Token / API Key)"), fontSize = 12.sp) },
                        placeholder = { Text(Translator.tr("اختياري — للنشر المباشر"), color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    if (linkVerifyMsg != null) {
                        Text(linkVerifyMsg!!, color = if (linkVerifyMsg!!.contains("✅")) Color(0xFF10B981) else Color(0xFFEF4444), fontSize = 12.sp, fontFamily = CairoFont)
                    }
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
                            Toast.makeText(context, "تم حفظ بيانات الحساب بنجاح ✅", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    enabled = !isLinking
                ) {
                    Text(Translator.tr("حفظ"), color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { linkDialogState = null }, enabled = !isLinking) {
                    Text(Translator.tr("إلغاء"), color = Color.Gray, fontFamily = CairoFont)
                }
            },
            containerColor = CardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // حوار محرر القنوات الرسمية
    if (channelDraftId != null && canManageChannels) {
        val editingId = channelDraftId!!
        val isFixed = editingId in SocialAccountManager.FIXED_CHANNEL_IDS
        AlertDialog(
            onDismissRequest = { channelDraftId = null },
            containerColor = CardSurface,
            title = { Text(if (editingId.isBlank()) "إضافة رابط رسمي" else "تعديل الرابط", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isFixed) {
                        OutlinedTextField(value = chPlatform, onValueChange = { chPlatform = it }, label = { Text("اسم المنصة", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = chDisplay, onValueChange = { chDisplay = it }, label = { Text("الاسم المعروض", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(value = chHandle, onValueChange = { chHandle = it }, label = { Text("المعرّف (handle)", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = chUrl, onValueChange = { chUrl = it; chUrlError = false },
                        label = { Text("الرابط https://...", fontSize = 11.sp) }, singleLine = true,
                        isError = chUrlError, modifier = Modifier.fillMaxWidth()
                    )
                    if (chUrlError) Text("أدخل رابطاً صالحاً يبدأ بـ http", color = Color(0xFFE53935), fontSize = 11.sp, fontFamily = CairoFont)
                    if (!isFixed) {
                        OutlinedTextField(value = chDesc, onValueChange = { chDesc = it }, label = { Text("الوصف (اختياري)", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = chUrl.trim()
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            chUrlError = true
                            return@Button
                        }
                        if (editingId.isBlank()) {
                            val id = "custom_" + System.currentTimeMillis()
                            SocialAccountManager.saveCustomChannel(
                                context,
                                OfficialChannelInfo(
                                    id = id,
                                    platformName = chPlatform.trim().ifBlank { "رابط" },
                                    handle = chHandle.trim(),
                                    displayName = chDisplay.trim().ifBlank { chPlatform.trim().ifBlank { "رابط رسمي" } },
                                    description = chDesc.trim(),
                                    url = url,
                                    followersDisplay = "رسمي ✦"
                                )
                            )
                            Toast.makeText(context, "أُضيف الرابط ✅", Toast.LENGTH_SHORT).show()
                        } else if (isFixed) {
                            SocialAccountManager.updateOfficialChannel(context, editingId, chHandle, url)
                            Toast.makeText(context, "حُفظ التعديل ✅", Toast.LENGTH_SHORT).show()
                        } else {
                            val old = officialChannels.firstOrNull { it.id == editingId }
                            if (old != null) {
                                SocialAccountManager.saveCustomChannel(
                                    context,
                                    old.copy(
                                        platformName = chPlatform.trim().ifBlank { old.platformName },
                                        displayName = chDisplay.trim().ifBlank { old.displayName },
                                        handle = chHandle.trim(),
                                        description = chDesc.trim(),
                                        url = url
                                    )
                                )
                                Toast.makeText(context, "حُفظ التعديل ✅", Toast.LENGTH_SHORT).show()
                            }
                        }
                        channelDraftId = null
                        refreshChannels()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) { Text("حفظ", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { channelDraftId = null }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            }
        )
    }

    // التنقل بين الشاشات الفرعية المستقلة
    AnimatedContent(
        targetState = currentSubScreen,
        transitionSpec = {
            if (targetState != SettingsSubScreen.MAIN) {
                slideInHorizontally { width -> -width } + fadeIn() togetherWith slideOutHorizontally { width -> width } + fadeOut()
            } else {
                slideInHorizontally { width -> width } + fadeIn() togetherWith slideOutHorizontally { width -> -width } + fadeOut()
            }
        },
        label = "SettingsSubScreenAnimation"
    ) { subScreen ->
        when (subScreen) {
            // ==========================================
            // 🏠 الشاشة الرئيسية للإعدادات (Main Settings)
            // ==========================================
            SettingsSubScreen.MAIN -> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    Translator.tr("الإعدادات الحسابية والفنية"),
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                                }
                            },
                            actions = {
                                IconButton(onClick = onLogout) {
                                    Icon(
                                        if (isGuest) Icons.Default.Login else Icons.Default.Logout,
                                        contentDescription = "تسجيل",
                                        tint = if (isGuest) GoldPrimary else Color(0xFFEF4444)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
                        )
                    },
                    bottomBar = bottomBar,
                    containerColor = qabasBackground()
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. بطاقة الملف الشخصي الفاخرة المحدثة بنظام HUD الفضائي
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .luxuryCardStyle(shapeRadius = 22.dp, borderAlpha = 0.45f, glowElevation = 8.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box {
                                        Surface(
                                            modifier = Modifier.size(58.dp),
                                            shape = CircleShape,
                                            color = Color(0xFF0F172A),
                                            border = BorderStroke(2.dp, UiBrush.horizontalGradient(listOf(GoldPrimary, Color(0xFF38BDF8))))
                                        ) {
                                            if (avatarUriString.isNotBlank()) {
                                                AsyncImage(
                                                    model = avatarUriString,
                                                    contentDescription = "Avatar",
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().background(
                                                        UiBrush.radialGradient(
                                                            colors = listOf(GoldPrimary.copy(alpha = 0.35f), Color(0xFF0F172A))
                                                        )
                                                    ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = when (selectedPresetAvatar) {
                                                            1 -> Icons.Default.AutoAwesome
                                                            2 -> Icons.Default.MovieFilter
                                                            3 -> Icons.Default.MenuBook
                                                            4 -> Icons.Default.Psychology
                                                            else -> Icons.Default.Person
                                                        },
                                                        contentDescription = null,
                                                        tint = GoldPrimary,
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                }
                                            }
                                        }
                                        // مؤشر متصل أونلاين 🟢
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldGreen)
                                                .border(2.dp, DeepSlate, CircleShape)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = savedDisplayName,
                                                color = Color.White,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp
                                            )
                                        }
                                        Text(
                                            text = if (isGuest) "guest@qabas.studio" else userEmail,
                                            color = Color(0xFF94A3B8),
                                            fontFamily = NotoSansFont,
                                            fontSize = 11.5.sp
                                        )

                                        // المعرّف الخاص الفريد مع زر النسخ الفوري
                                        val customId = remember { UserIdentifierManager.getCustomId(context) }
                                        Surface(
                                            color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .clickable { UserIdentifierManager.copyToClipboard(context, customId) }
                                                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = Color(0xFF00E5FF),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "المعرّف: ${UserIdentifierManager.formatDisplay(customId)}",
                                                    color = Color(0xFF00E5FF),
                                                    fontFamily = RobotoMonoFont,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "نسخ المعرّف",
                                                    tint = Color(0xFF00E5FF).copy(alpha = 0.8f),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = if (isAdmin || isRelative) GoldPrimary else Color(0xFF1E293B),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (isAdmin || isRelative) GoldPrimary else Color(0xFF334155))
                                            ) {
                                                Text(
                                                    text = when {
                                                        isAdmin -> Translator.tr("مطور النظام (صلاحيات كاملة) 👑")
                                                        isRelative -> Translator.tr("حساب الاكتفاء الذاتي 🔑")
                                                        isGuest -> Translator.tr("حساب مجاني 🏷️")
                                                        else -> Translator.tr("عضوية ذهبية Pro ⭐")
                                                    },
                                                    color = if (isAdmin || isRelative) DeepSlate else Color.White,
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (!isAdmin && !isGuest && !isRelative) {
                                        IconButton(onClick = onNavigateToPremiumUpgrade) {
                                            Icon(Icons.Default.WorkspacePremium, contentDescription = "ترقية", tint = GoldPrimary, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // ── مراكز التحكم الموحدة (Unified Control Hubs) ──
                        SettingsGroupHeader(Translator.tr("مراكز الضبط والتحكم الذكي ✦"))

                        // 1. استوديو الواجهة والتفاعل (UX & Interaction)
                        SettingsNavigationCard(
                            title = "استوديو الواجهة والتفاعل",
                            subtitle = "الثيمات، لغة التطبيق، الأنميشن، والمؤثرات الصوتية",
                            icon = Icons.Default.AutoAwesomeMotion,
                            badge = "تجربة المستخدم ✦",
                            accentColor = Color(0xFF00E5FF),
                            onClick = { currentSubScreen = SettingsSubScreen.UX_DESIGN_STUDIO }
                        )

                        // 1.1 استوديو المؤثرات الصوتية المباشر (Sound Studio)
                        SettingsNavigationCard(
                            title = "استوديو المؤثرات الصوتية والصدى",
                            subtitle = "التحكم بالأصوات التفاعلية، مستوى الصوت، أنماط النقر والاهتزاز",
                            icon = Icons.Default.GraphicEq,
                            badge = "صوتيات 🔊",
                            accentColor = Color(0xFF38BDF8),
                            onClick = { currentSubScreen = SettingsSubScreen.SOUND_STUDIO }
                        )

                        // 2. الاستوديو الروحاني والمصحف الشريف (Spiritual & Reading)
                        SettingsNavigationCard(
                            title = "الاستوديو الروحاني والمصحف الشريف",
                            subtitle = "تخصيص القراءة، الأذان، مواقيت الصلاة، والأذكار",
                            icon = Icons.Default.MenuBook,
                            badge = "زاد إيماني 🕋",
                            accentColor = Color(0xFF10B981),
                            onClick = { currentSubScreen = SettingsSubScreen.SPIRITUAL_STUDIO }
                        )

                        // 2.5 مركز الدعم والدردشة المباشرة (Support Center) - المطلب الرابع
                        val allReports by BugReportRepository.reports.collectAsState()
                        val unreadReportsCount = remember(allReports) { allReports.count { !it.isReadByUser } }
                        SettingsNavigationCard(
                            title = "مركز الدعم والدردشة المباشرة",
                            subtitle = "متابعة بلاغاتك، التواصل مع المطور، والحصول على مساعدة تقنية حية",
                            icon = Icons.Default.SupportAgent,
                            badge = if (unreadReportsCount > 0) "$unreadReportsCount ردود جديدة 🔔" else "الدعم الفني 💬",
                            badgeColor = if (unreadReportsCount > 0) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                            accentColor = if (unreadReportsCount > 0) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                            onClick = {
                                AppSoundManager.playClick()
                                showUserReportsDialog = true
                                // تمييز الكل كمقروء عند فتح المركز
                                coroutineScope.launch {
                                    BugReportRepository.markReportsAsRead(context)
                                }
                            }
                        )

                        // 3. مركز الإنتاج والذكاء الاحترافي (Pro Production)
                        SettingsNavigationCard(
                            title = "مركز الإنتاج والذكاء الاحترافي",
                            subtitle = "هوية الذكاء، العلامة المائية، وجودة التصدير",
                            icon = Icons.Default.Psychology,
                            badge = "إبداع احترافي 🎬",
                            accentColor = Color(0xFFA855F7),
                            onClick = { currentSubScreen = SettingsSubScreen.PRO_PRODUCTION_HUB }
                        )

                        // 3.4 إعدادات الإشعارات والتنبيهات الإيمانية
                        SettingsNavigationCard(
                            title = Translator.tr("إعدادات الإشعارات والتنبيهات الإيمانية"),
                            subtitle = Translator.tr("أوقات الأذان، تنبيه السحر، الفجر، الحديث اليومي، وتخصيص أدوات الإشعار"),
                            icon = Icons.Default.NotificationsActive,
                            badge = "تفاعلي 🔔",
                            accentColor = Color(0xFFF59E0B),
                            onClick = { currentSubScreen = SettingsSubScreen.NOTIFICATION_SETTINGS }
                        )

                        // 3.5 مركز الأذونات وحماية النظام (Permissions & System Sentinel)
                        SettingsNavigationCard(
                            title = Translator.tr("مركز الأذونات وحماية النظام"),
                            subtitle = Translator.tr("صلاحيات الميكروفون، الكاميرا، دقة الأذان، ودرع حماية الخلفية"),
                            icon = Icons.Default.Shield,
                            badge = "درع فضائي 🛡️",
                            accentColor = Color(0xFF00E5FF),
                            onClick = { currentSubScreen = SettingsSubScreen.PERMISSIONS_HUB }
                        )

                        // 4. النظام، البيانات والمجتمع (System & Community)
                        SettingsNavigationCard(
                            title = Translator.tr("النظام والبيانات والمجتمع"),
                            subtitle = Translator.tr("النسخ الاحتياطي، الكاش، حول التطبيق، والروابط الرسمية"),
                            icon = Icons.Default.SettingsSuggest,
                            badge = "الاستدامة ⚙️",
                            accentColor = GoldPrimary,
                            onClick = { currentSubScreen = SettingsSubScreen.COMMUNITY_SUPPORT }
                        )

                        // 4.1 اتصال GitHub المباشر وتحديثات xdelta الذكية (أداة مطوّر)
                        if (isAdmin || isDevMode) {
                            SettingsNavigationCard(
                                title = Translator.tr("تحديثات xdelta ومستودع GitHub المباشر"),
                                subtitle = Translator.tr("اتصال مباشر بـ Ali-awwww/qabasapp، فحص إصدارات xdelta، وتثبيت تفاضلي فائق السرعة"),
                                icon = Icons.Default.CloudSync,
                                badge = "تحديث تفاضلي ⚡",
                                accentColor = Color(0xFF00E5FF),
                                onClick = { currentSubScreen = SettingsSubScreen.GITHUB_XDELTA_HUB }
                            )
                        }

                        // 4.2 قسم تحكم زر الكاميرا الفضائي للبلاغات والمشاكل (يظهر للحسابات المسجلة فقط)
                        if (isLoggedInAccount) {
                            SettingsNavigationCard(
                                title = Translator.tr("زر الكاميرا الفضائي للبلاغات والمشاكل"),
                                subtitle = Translator.tr("التحكم الكامل بزر الكاميرا العائم: التفعيل، التعطيل، الشفافية، الاستقرار، وسلوك الالتقاط الفوري"),
                                icon = Icons.Default.CameraAlt,
                                badge = "خاص بالحسابات 📸",
                                accentColor = Color(0xFF00E5FF),
                                onClick = { currentSubScreen = SettingsSubScreen.BUG_CAMERA_CONTROL }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // زر تسجيل الخروج السريع
                        val logoutShape = androidx.compose.foundation.shape.CutCornerShape(8.dp)
                        Button(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A24)),
                            border = BorderStroke(1.dp, if (isGuest) GoldPrimary else Color(0xFFEF4444)),
                            shape = logoutShape
                        ) {
                            Icon(if (isGuest) Icons.Default.Login else Icons.Default.Logout, contentDescription = null, tint = if (isGuest) GoldPrimary else Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isGuest) Translator.tr("تسجيل الدخول / إنشاء حساب") else Translator.tr("تسجيل الخروج من الحساب"),
                                color = if (isGuest) GoldPrimary else Color(0xFFEF4444),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            // ==========================================
            // 🎨 1. استوديو الواجهة والتفاعل (UX & Design)
            // ==========================================
            SettingsSubScreen.UX_DESIGN_STUDIO -> {
                SubScreenScaffold(
                    title = "استوديو الواجهة والتفاعل",
                    icon = Icons.Default.AutoAwesomeMotion,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // 1.1 الديزاين والثيمات
                        SettingsActionCard(
                            title = "نظام الألوان والديزاين (Global Theme)",
                            subtitle = "تخصيص الهوية البصرية، نمط النيون، ومعاينة الثيمات",
                            icon = Icons.Default.Palette,
                            accentColor = Color(0xFF00E5FF),
                            onClick = { currentSubScreen = SettingsSubScreen.CYBER_HUD_PREVIEW }
                        )

                        // 1.2 لغة التطبيق
                        SettingsActionCard(
                            title = "لغة التطبيق (Interface Language)",
                            subtitle = "التبديل بين العربية والإنجليزية",
                            icon = Icons.Default.Language,
                            accentColor = GoldPrimary,
                            content = { LanguageSelectionSection() }
                        )
                    }
                }
            }

            // ==========================================
            // 🕋 2. الاستوديو الروحاني والمصحف الشريف
            // ==========================================
            SettingsSubScreen.SPIRITUAL_STUDIO -> {
                SubScreenScaffold(
                    title = "الاستوديو الروحاني والمصحف",
                    icon = Icons.Default.MenuBook,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingsActionCard(
                            title = "تخصيص القراءة والمصحف",
                            subtitle = "حجم الخط، نوع الرسم، وأنماط الورق",
                            icon = Icons.Default.AutoStories,
                            accentColor = Color(0xFF10B981),
                            content = { ReadingCustomizationSection() }
                        )
                        SettingsActionCard(
                            title = "الأذان ومواقيت الصلاة",
                            subtitle = "حساب المواقيت، اختيار المؤذن، وتنبيهات الأذكار",
                            icon = Icons.Default.Mosque,
                            accentColor = Color(0xFFF59E0B),
                            content = { SpiritualPreferencesSection() }
                        )
                    }
                }
            }

            // ==========================================
            // 🎬 3. مركز الإنتاج والذكاء الاحترافي
            // ==========================================
            SettingsSubScreen.PRO_PRODUCTION_HUB -> {
                SubScreenScaffold(
                    title = "مركز الإنتاج والذكاء الاحترافي",
                    icon = Icons.Default.Psychology,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingsActionCard(
                            title = "هوية الذكاء الاصطناعي",
                            subtitle = "تنسيق نبرة وأسلوب المخرج الذكي",
                            icon = Icons.Default.AutoAwesome,
                            accentColor = Color(0xFFA855F7),
                            content = { AiIdentitySection(onNavigateToTasteProfile) }
                        )
                        SettingsActionCard(
                            title = "العلامة المائية وجودة التصدير",
                            subtitle = "تخصيص الشعار ودقة الفيديو النهائية",
                            icon = Icons.Default.VideoSettings,
                            accentColor = Color(0xFF0EA5E9),
                            content = { StudioWatermarkPreferencesSection() }
                        )
                    }
                }
            }

            // ==========================================
            // ⚙️ 4. النظام، البيانات والمجتمع
            // ==========================================
            SettingsSubScreen.COMMUNITY_SUPPORT -> {
                SubScreenScaffold(
                    title = "النظام والبيانات والمجتمع",
                    icon = Icons.Default.SettingsSuggest,
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    bottomBar = bottomBar
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        // ── البيانات والنسخ الاحتياطي ──
                        SettingsGroupHeader(Translator.tr("البيانات والنسخ الاحتياطي ☁️"))
                        SettingsActionCard(
                            title = "النسخ السحابي وإدارة الذاكرة",
                            subtitle = "تصدير الإعدادات، استعادة البيانات، وتفريغ الكاش",
                            icon = Icons.Default.CloudSync,
                            accentColor = Color(0xFF6366F1),
                            content = {
                                StorageAndBackupSection(
                                    cacheSizeBytes = cacheSizeBytes,
                                    isDeveloper = isAdmin || isDevMode,
                                    onClearCache = {
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                context.cacheDir.deleteRecursively()
                                                context.cacheDir.mkdirs()
                                            }
                                            cacheSizeBytes = 0L
                                            Toast.makeText(context, "تم تنظيف الملفات المؤقتة بنجاح 🧹", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        )

                        // ── المجتمع والتواصل ──
                        SettingsGroupHeader(Translator.tr("المجتمع والتواصل ✦"))
                        SettingsActionCard(
                            title = "ملتقى قبس والمجتمعات الرسمية",
                            subtitle = "قنوات التواصل، مجتمع المبدعين، والدعم الفني المباشر",
                            icon = Icons.Default.Hub,
                            accentColor = GoldPrimary,
                            content = { CommunityAndLinksSection() }
                        )
                        CommunityReviewsCard()

                        // ── المنظومة والسياسات ──
                        SettingsGroupHeader(Translator.tr("المنظومة والسياسات 📜"))
                        SettingsActionCard(
                            title = "منظومة قبس والضوابط الشرعية",
                            subtitle = "الرؤية، الإصدار، سجل التحديثات، والاعتماد الشرعي",
                            icon = Icons.Default.VerifiedUser,
                            accentColor = Color(0xFF38BDF8),
                            onClick = onNavigateToAboutUs
                        )
                        SettingsActionCard(
                            title = "سجل التحديثات التفصيلي",
                            subtitle = "استعرض تاريخ تطور منظومة قبس والمزايا الجديدة",
                            icon = Icons.Default.History,
                            accentColor = GoldPrimary,
                            onClick = onNavigateToHistory
                        )
                        SettingsActionCard(
                            title = "القواعد والسياسات الموحدة",
                            subtitle = "شروط الاستخدام، سياسة الخصوصية، وقواعد المجتمع",
                            icon = Icons.Default.Gavel,
                            accentColor = Color(0xFFF59E0B),
                            onClick = onNavigateToRules
                        )

                        // ── الدعم والتشخيص ──
                        SettingsGroupHeader(Translator.tr("الدعم والتشخيص 🛠️"))
                        if (!isGuest) {
                            SettingsActionCard(
                                title = "سجل بلاغاتي واقتراحاتي للتحسين",
                                subtitle = "متابعة حالة الأعطال والاقتراحات المرسلة وردود فريق التطوير",
                                icon = Icons.Default.ListAlt,
                                accentColor = Color(0xFFF59E0B),
                                onClick = { showUserReportsDialog = true }
                            )
                        }
                        if (isAdmin || isDevMode) {
                            SettingsActionCard(
                                title = "سجل وملتقط الانهيارات والأعطال (Crash Guard)",
                                subtitle = "تشخيص الأعطال الحية، استخراج السباك، وتصدير تقارير الأمان",
                                icon = Icons.Default.BugReport,
                                accentColor = Color(0xFFFF2A85),
                                onClick = { currentSubScreen = SettingsSubScreen.CRASH_LOGS }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 🎨 شاشة معاينة نظام الألوان (Design Studio Preview)
            // ==========================================
            SettingsSubScreen.CYBER_HUD_PREVIEW -> {
                ThemeDesignStudioScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.UX_DESIGN_STUDIO }
                )
            }

            // ==========================================
            // 🔊 استوديو المؤثرات الصوتية الشامل (Sound Effects Studio)
            // ==========================================
            SettingsSubScreen.SOUND_STUDIO -> {
                SoundEffectsSubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }

            // ==========================================
            // 🛡️ مركز الأذونات وحماية النظام (Spatial Permissions HUD)
            // ==========================================
            SettingsSubScreen.PERMISSIONS_HUB -> {
                AppPermissionsHubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }

            // ==========================================
            // 💥 مركز سجل وملتقط الانهيارات السيبراني (Crash Guard HUD)
            // ==========================================
            SettingsSubScreen.CRASH_LOGS -> {
                CrashLogsHubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.COMMUNITY_SUPPORT }
                )
            }

            // ==========================================
            // 🔔 مركز إعدادات الإشعارات والتنبيهات الإيمانية
            // ==========================================
            SettingsSubScreen.NOTIFICATION_SETTINGS -> {
                NotificationSettingsScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }

            // ==========================================
            // ⚡ مركز تحديثات xdelta المباشر مع GitHub
            // ==========================================
            SettingsSubScreen.GITHUB_XDELTA_HUB -> {
                GithubXdeltaHubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN }
                )
            }

            // ==========================================
            // 📸 مركز تحكم زر الكاميرا الفضائي للبلاغات والمشاكل
            // ==========================================
            SettingsSubScreen.BUG_CAMERA_CONTROL -> {
                BugCameraSettingsScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                    onOpenMyReports = { showUserReportsDialog = true }
                )
            }
        }
    }

    if (showUserReportsDialog) {
        UserReportsDialog(onDismissRequest = { showUserReportsDialog = false })
    }

    // Dialogs
    if (showAboutAppDialog) {
        AlertDialog(
            onDismissRequest = { showAboutAppDialog = false },
            containerColor = CardSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("منظومة قبس (Qabas Studio)", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "﴿ إِذْ رَأَىٰ نَارًا فَقَالَ لِأَهْلِهِ امْكُثُوا إِنِّي آنَسْتُ نَارًا لَّعَلِّي آتِيكُم مِّنْهَا بِقَبَسٍ ﴾",
                        color = Color(0xFFF8FAFC),
                        fontFamily = AmiriFont,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "قبس هو استوديو متكامل في جيبك لصناعة المحتوى الدعوي والإسلامي الهادف بالذكاء الاصطناعي، يجمع بين تلاوة وتدبر القرآن، الصحيحين، الإخراج، والتعليق الصوتي.",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutAppDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)) {
                    Text("إغلاق", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            containerColor = CardSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("سجل تحديثات قبس v${BuildConfig.VERSION_NAME}", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val savedNotes = prefs.getString("last_release_notes", "").orEmpty()
                    Text(savedNotes.ifBlank { "تحديثات مستمرة في الأداء والمظهر والاستقرار." }, color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showChangelogDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)) {
                    Text("رائع ✦", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = CardSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الضوابط الشرعية وسياسة الخصوصية", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ميثاق النزاهة والمحتوى الهادف:", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("1. يلتزم تطبيق قبس بضمان خلو المحتوى من أي مخالفات شرعية.", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                    Text("2. جميع التلاوات والأناشيد والمؤثرات الصوتية خالية من المعازف.", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                    Text("3. مفاتيح الـ API والبيانات تحفظ محلياً ومشفرة.", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)) {
                    Text("أوافق وملتزم", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showPassphraseDialog) {
        AlertDialog(
            onDismissRequest = { showPassphraseDialog = false },
            containerColor = Color(0xFF0D1117),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = AiViolet, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تفعيل وضع المطور", color = AiViolet, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل كلمة المرور السرية لتفعيل وضع المطور:", color = Color.White, fontFamily = CairoFont, fontSize = 13.sp)
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it; passphraseError = false },
                        placeholder = { Text("كلمة المرور", color = Color.Gray, fontFamily = CairoFont) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passphraseError) {
                        Text("كلمة المرور غير صحيحة", color = Color(0xFFF97316), fontFamily = CairoFont, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val devPrefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                        val salt = "Qbs" + "::DevGate::" + "v1"
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                            .digest((passphraseInput + salt).toByteArray())
                            .joinToString("") { "%02x".format(it) }
                        val expected = "284308ac" + "cd46b74003578263" + "92758c1e0d189e543" + "43d3777156a550cbb123bfa"
                        if (digest == expected) {
                            devPrefs.edit().putBoolean("is_developer", true).apply()
                            isDevMode = true
                            showPassphraseDialog = false
                            passphraseInput = ""
                            Toast.makeText(context, "تم تفعيل وضع المطور! 🛠️", Toast.LENGTH_LONG).show()
                        } else {
                            passphraseError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AiViolet)
                ) {
                    Text("تفعيل", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false }) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            }
        )
    }
}

/**
 * 🔊 شاشة تخصيص نمط ونظام المؤثرات الصوتية (Sound Effects Studio)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundEffectsSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = qabasPrimary()
    
    val masterEnabled by AppSoundManager.masterEnabled.collectAsState()
    val currentPreset by AppSoundManager.currentPreset.collectAsState()
    val currentVolume by AppSoundManager.volume.collectAsState()
    val clickEnabled by AppSoundManager.clickEnabled.collectAsState()
    val animEnabled by AppSoundManager.animEnabled.collectAsState()
    val dhikrEnabled by AppSoundManager.dhikrEnabled.collectAsState()
    val hapticEnabled by AppSoundManager.hapticEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "نظام المؤثرات الصوتية",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = primaryColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "SFX Studio 🔊",
                                color = primaryColor,
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        },
        containerColor = qabasBackground()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. بطاقة التفعيل العام للمؤثرات (Master Power Switch) ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (masterEnabled) Color(0xFF0F1E36) else Color(0xFF1E293B)),
                border = BorderStroke(1.2.dp, if (masterEnabled) primaryColor else Color.Gray.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (masterEnabled) primaryColor.copy(alpha = 0.2f) else Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (masterEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (masterEnabled) primaryColor else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Translator.tr("المؤثرات الصوتية العامة"),
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (masterEnabled) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (masterEnabled) "نشط ✦" else "معطّل",
                                        color = if (masterEnabled) Color(0xFF34D399) else Color(0xFFF87171),
                                        fontFamily = CairoFont,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (masterEnabled) Translator.tr("تشغيل أصوات النقر، التحولات، والمسبحة") else Translator.tr("كتم كافة المؤثرات التفاعلية"),
                                color = Color.Gray,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = {
                            AppSoundManager.setMasterEnabled(context, it)
                            if (it) AppSoundManager.playActivate()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = primaryColor,
                            checkedTrackColor = primaryColor.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // ── 2. بطاقة التحكم بمستوى الصوت الرئيسي ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (currentVolume > 0.5f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(Translator.tr("مستوى صوت المؤثرات"), color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(Translator.tr("التحكم في قوة صدى النقر والتفاعلات الحية"), color = Color.Gray, fontFamily = NotoSansFont, fontSize = 11.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = currentVolume,
                            onValueChange = {
                                AppSoundManager.setVolume(context, it)
                            },
                            onValueChangeFinished = {
                                AppSoundManager.playClick()
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = primaryColor,
                                activeTrackColor = primaryColor,
                                inactiveTrackColor = primaryColor.copy(alpha = 0.2f)
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${(currentVolume * 100).toInt()}%",
                            color = primaryColor,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.width(42.dp)
                        )
                    }

                    // أزرار الضبط السريع لمستوى الصوت (25%, 50%, 75%, 100%)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.25f to "25%", 0.50f to "50%", 0.75f to "75%", 1.0f to "100%").forEach { (vol, label) ->
                            val isCurrent = (currentVolume * 100).toInt() == (vol * 100).toInt()
                            OutlinedButton(
                                onClick = {
                                    AppSoundManager.setVolume(context, vol)
                                    AppSoundManager.playClick()
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isCurrent) primaryColor else Color.White.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isCurrent) primaryColor.copy(alpha = 0.15f) else Color.Transparent
                                )
                            ) {
                                Text(label, color = if (isCurrent) primaryColor else Color.Gray, fontSize = 11.sp, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── 3. مفاتيح تفعيل أنواع المؤثرات التفصيلية ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Translator.tr("تخصيص مجالات المؤثرات التفاعلية"), color = primaryColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 1. أصوات النقر واللمس
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(Translator.tr("أصوات النقر واللمس"), color = Color.White, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(Translator.tr("صدى صوتي عند الضغط على الأزرار والبطاقات"), color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.5.sp)
                                }
                            }
                            Switch(
                                checked = clickEnabled,
                                onCheckedChange = {
                                    AppSoundManager.setClickEnabled(context, it)
                                    if (it) AppSoundManager.playClick()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        // 2. أصوات الأنميشن والتحولات
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesomeMotion, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(Translator.tr("أصوات الأنميشن وانتقال الشاشات"), color = Color.White, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(Translator.tr("مؤثر انسيابي عند التنقل بين الشاشات الرئيسية"), color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.5.sp)
                                }
                            }
                            Switch(
                                checked = animEnabled,
                                onCheckedChange = {
                                    AppSoundManager.setAnimEnabled(context, it)
                                    if (it) AppSoundManager.playAnimationSound()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        // 3. أصوات السبحة والأذكار
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mosque, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(Translator.tr("أصوات السبحة والأذكار"), color = Color.White, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(Translator.tr("رنين حبات السبحة الخشبية مع كل تسبيحة"), color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.5.sp)
                                }
                            }
                            Switch(
                                checked = dhikrEnabled,
                                onCheckedChange = {
                                    AppSoundManager.setDhikrEnabled(context, it)
                                    if (it) AppSoundManager.playDhikr()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldPrimary.copy(alpha = 0.5f))
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        // 4. الاهتزاز التكتيكي التفاعلي
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Vibration, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(Translator.tr("الاهتزاز التكتيكي مع الصوت (Haptic)"), color = Color.White, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(Translator.tr("نبضة اهتزاز خفيفة مع كل نقرة وصوت"), color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.5.sp)
                                }
                            }
                            Switch(
                                checked = hapticEnabled,
                                onCheckedChange = {
                                    AppSoundManager.setHapticEnabled(context, it)
                                    if (it) AppSoundManager.playClick()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFA855F7), checkedTrackColor = Color(0xFFA855F7).copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }

            // ── 4. قائمة أنماط المؤثرات (Presets) ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Translator.tr("اختر نمط المؤثرات الصوتية المعتمد"), color = primaryColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                AppSoundPreset.entries.forEach { preset ->
                    val isSelected = currentPreset == preset
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                AppSoundManager.setPreset(context, preset)
                                AppSoundManager.playDemoSound(preset)
                                Toast.makeText(context, "تم تفعيل نمط: ${preset.title} 🔊", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF1E293B) else CardSurface),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) primaryColor else Color.Transparent)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) primaryColor.copy(alpha = 0.2f) else qabasBackground()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(preset.iconEmoji, fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(preset.title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(preset.badge, color = if (isSelected) primaryColor else Color.Gray, fontSize = 8.5.sp, fontFamily = CairoFont, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                                Text(preset.subtitle, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.5.sp, lineHeight = 14.sp)
                            }
                            
                            // زر معاينة الصوت المباشر للنمط
                            IconButton(
                                onClick = {
                                    AppSoundManager.playDemoSound(preset)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = "معاينة", tint = if (isSelected) primaryColor else Color.Gray, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // ── 5. منصة الاختبار السريع للتفاعلات الحية ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Translator.tr("منصة اختبار التفاعلات الصوتية الحية"), color = primaryColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            AppSoundManager.playClick()
                            Toast.makeText(context, "نقرة زر تفاعلية ⚡", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, primaryColor)
                    ) {
                        Text("نقرة زر 🔘", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            AppSoundManager.playToggle()
                            Toast.makeText(context, "مفتاح تبديل 🔄", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8).copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8))
                    ) {
                        Text("تبديل 🔄", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            AppSoundManager.playDhikr()
                            Toast.makeText(context, "نقرة سبحة خشبية 📿", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, GoldPrimary)
                    ) {
                        Text("حبة سبحة 📿", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            AppSoundManager.playSuccess()
                            Toast.makeText(context, "رنين إنجاز ونجاح 🌟", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981).copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Text("إنجاز 🌟", color = Color(0xFF34D399), fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
/**
 * 🌐 شاشة اختيار لغة التطبيق (Language Selection Studio)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = qabasPrimary()
    val currentLang by UserPreferencesManager.appLanguage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Translator.tr("لغة التطبيق"),
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = primaryColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Language 🌐",
                                color = primaryColor,
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        },
        containerColor = qabasBackground()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. بطاقة التعريف بذكاء الترجمة ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Translator.tr("تخصيص لغة الواجهة"),
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = Translator.tr("اختر اللغة المفضلة لاستخدام التطبيق. سيتم تحديث كافة المسميات، الأزرار، والرسائل فوراً دون الحاجة لإعادة تشغيل التطبيق."),
                        color = Color.Gray,
                        fontFamily = NotoSansFont,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // ── 2. قائمة اللغات المتاحة ──
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple("ar", "العربية (Arabic)", "🇪🇬"),
                    Triple("en", "English (الإنجليزية)", "🇺🇸")
                ).forEach { (langCode, langName, flag) ->
                    val isSelected = currentLang == langCode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                UserPreferencesManager.setAppLanguage(context, langCode)
                                AppSoundManager.playSuccess()
                                Toast.makeText(context, Translator.tr("تم تغيير اللغة بنجاح!"), Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) primaryColor.copy(alpha = 0.12f) else CardSurface
                        ),
                        border = BorderStroke(
                            if (isSelected) 1.6.dp else 1.dp,
                            if (isSelected) primaryColor else Color.White.copy(alpha = 0.05f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(flag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = langName,
                                        color = if (isSelected) primaryColor else Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.5.sp
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = Translator.tr("اللغة النشطة حالياً"),
                                            color = primaryColor.copy(alpha = 0.8f),
                                            fontFamily = NotoSansFont,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── زر العودة ──
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = DeepSlate)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Translator.tr("العودة للإعدادات"),
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenScaffold(
    title: String,
    icon: ImageVector,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (ColumnScope.() -> Unit)
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = Translator.tr(title),
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        },
        bottomBar = bottomBar,
        containerColor = qabasBackground()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

/**
 * بطاقة إجراء داخلية في الشاشات الفرعية تدعم طي المحتوى أو الانتقال
 */
@Composable
fun SettingsActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    val shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.25f, glowElevation = 4.dp)
            .clip(shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (onClick != null) it.clickable { onClick() } else it },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(Translator.tr(title), color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Translator.tr(subtitle), color = Color.Gray, fontFamily = NotoSansFont, fontSize = 10.5.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }
            if (content != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
fun LanguageSelectionSection() {
    val context = LocalContext.current
    val currentLang by UserPreferencesManager.appLanguage.collectAsState()
    val primaryColor = qabasPrimary()
    val shape = androidx.compose.foundation.shape.CutCornerShape(8.dp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("ar", "العربية (Arabic)", "🇪🇬"),
            Triple("en", "English (الإنجليزية)", "🇺🇸")
        ).forEach { (langCode, langName, flag) ->
            val isSelected = currentLang == langCode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (isSelected) primaryColor.copy(alpha = 0.12f) else Color(0xFF151B2B))
                    .border(1.dp, if (isSelected) primaryColor else Color.White.copy(alpha = 0.05f), shape)
                    .clickable {
                        UserPreferencesManager.setAppLanguage(context, langCode)
                        AppSoundManager.playSuccess()
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(flag, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(langName, color = if (isSelected) primaryColor else Color.White, fontFamily = CairoFont, fontSize = 13.sp)
                }
                if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SoundAndMotionSection(
    onOpenSoundStudio: () -> Unit = {}
) {
    val context = LocalContext.current
    val masterEnabled by AppSoundManager.masterEnabled.collectAsState()
    val currentPreset by AppSoundManager.currentPreset.collectAsState()
    val currentVolume by AppSoundManager.volume.collectAsState()
    val clickEnabled by AppSoundManager.clickEnabled.collectAsState()
    val animEnabled by AppSoundManager.animEnabled.collectAsState()
    val currentAnim by AppAnimationManager.currentAnimation.collectAsState()
    val primaryColor = qabasPrimary()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // زر فتح استوديو المؤثرات المتكامل
        Button(
            onClick = {
                AppSoundManager.playClick()
                onOpenSoundStudio()
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, primaryColor)
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(Translator.tr("فتح استوديو المؤثرات الصوتية الشامل (SFX Studio) 🔊"), color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // المفتاح العام للمؤثرات
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(Translator.tr("المؤثرات الصوتية العامة"), color = Color.White, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(Translator.tr("النمط الحالي: ") + currentPreset.title, color = primaryColor, fontFamily = CairoFont, fontSize = 10.5.sp)
            }
            Switch(
                checked = masterEnabled,
                onCheckedChange = {
                    AppSoundManager.setMasterEnabled(context, it)
                    if (it) AppSoundManager.playClick()
                },
                colors = SwitchDefaults.colors(checkedThumbColor = primaryColor)
            )
        }

        // شريط مستوى الصوت السريع
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = currentVolume,
                onValueChange = { AppSoundManager.setVolume(context, it) },
                onValueChangeFinished = { AppSoundManager.playClick() },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("${(currentVolume * 100).toInt()}%", color = primaryColor, fontFamily = RobotoMonoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(Translator.tr("أصوات النقر واللمس"), color = Color.White, fontFamily = CairoFont, fontSize = 13.sp)
            Switch(checked = clickEnabled, onCheckedChange = { AppSoundManager.setClickEnabled(context, it) }, colors = SwitchDefaults.colors(checkedThumbColor = primaryColor))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(Translator.tr("أصوات الأنميشن والتحولات"), color = Color.White, fontFamily = CairoFont, fontSize = 13.sp)
            Switch(checked = animEnabled, onCheckedChange = { AppSoundManager.setAnimEnabled(context, it) }, colors = SwitchDefaults.colors(checkedThumbColor = primaryColor))
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.CutCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MotionPhotosAuto, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(Translator.tr("نمط الأنميشن النشط: ") + Translator.tr(currentAnim.title), color = primaryColor, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AiIdentitySection(onNavigateToTasteProfile: () -> Unit) {
    Button(
        onClick = onNavigateToTasteProfile,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.CutCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1C2E)),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
    ) {
        Icon(Icons.Default.Psychology, contentDescription = null, tint = GoldPrimary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(Translator.tr("تخصيص محرك الذوق (Taste Engine)"), color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CommunityAndLinksSection() {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val shape = androidx.compose.foundation.shape.CutCornerShape(10.dp)

    fun openUrlSafely(url: String, toastSuccessMsg: String) {
        AppSoundManager.playToggle()
        val success = QabasLinkOpener.openUrl(context, url, toastSuccessMsg)
        if (success) {
            Toast.makeText(context, toastSuccessMsg, Toast.LENGTH_SHORT).show()
        }
    }

    val communityLinks = listOf(
        CommunityLinkItem(
            title = "مجتمع قبس الرسمي ✦ فيسبوك",
            subtitle = "الصفحة والمجتمع الرسمي لمتابعة الجديد والمشاركات والتفاعل",
            url = "https://www.facebook.com/share/18p5cMSJ17/",
            icon = Icons.Default.Public,
            color = Color(0xFF1877F2),
            badge = "فيسبوك الرسمي"
        ),
        CommunityLinkItem(
            title = "ملتقى المبدعين ✦ واتساب",
            subtitle = "مجتمع صُنّاع المحتوى القرآني والحديثي والمصممين لتبادل الخبرات",
            url = "https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=sh&p=a&mlu=4&ilr=4&iam=2",
            icon = Icons.Default.Forum,
            color = Color(0xFF25D366),
            badge = "مجتمع تفاعلي"
        ),
        CommunityLinkItem(
            title = "الموقع الرسمي ✦ قبس",
            subtitle = "بوابة قبس الرسمية للتحميل والأخبار والمحاضرات",
            url = "https://alippappa7-maker.github.io/Website-/",
            icon = Icons.Default.Language,
            color = Color(0xFF0EA5E9),
            badge = "الموقع الرسمي"
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        communityLinks.forEach { item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        openUrlSafely(item.url, "جاري فتح ${item.title}...")
                    },
                shape = shape,
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, item.color.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(item.color.copy(alpha = 0.15f))
                            .border(1.dp, item.color.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            Translator.tr(item.title),
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = item.color.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.6.dp, item.color.copy(alpha = 0.3f))
                        ) {
                            Text(
                                item.badge,
                                color = item.color,
                                fontSize = 9.5.sp,
                                fontFamily = CairoFont,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            Translator.tr(item.subtitle),
                            color = Color.Gray,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Copy action button
                    IconButton(
                        onClick = {
                            AppSoundManager.playToggle()
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.url))
                            Toast.makeText(context, "تم نسخ رابط ${item.title} 📋", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الرابط", tint = item.color.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = item.color.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

private data class CommunityLinkItem(
    val title: String,
    val subtitle: String,
    val url: String,
    val icon: ImageVector,
    val color: Color,
    val badge: String
)

@Composable
fun AboutAppSection(onShowPrivacy: () -> Unit) {
    val shape = androidx.compose.foundation.shape.CutCornerShape(8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(Translator.tr("إصدار التطبيق"), color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp)
            Text("v2.5.0-HUD", color = GoldPrimary, fontFamily = RobotoMonoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onShowPrivacy,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(Translator.tr("الضوابط الشرعية والخصوصية"), color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
        }
    }
}

/**
 * بطاقة انتقال فاخرة ومحدثة بنظام HUD الفضائي لمنع التداخل والطي العمودي
 */
@Composable
fun SettingsNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String? = null,
    badgeColor: Color = GoldPrimary,
    accentColor: Color = GoldPrimary,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.35f, glowElevation = 5.dp)
            .clip(shape)
            .clickable { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            UiBrush.linearGradient(
                                listOf(
                                    accentColor.copy(alpha = 0.22f),
                                    accentColor.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(1.2.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = Translator.tr(title),
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (badge != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = badgeColor.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Text(
                                    text = Translator.tr(badge),
                                    color = badgeColor,
                                    fontFamily = CairoFont,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = Translator.tr(subtitle),
                        color = Color(0xFF94A3B8),
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp,
                        maxLines = 2,
                        lineHeight = 15.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "فتح الإعداد",
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsGroupHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    UiBrush.verticalGradient(
                        listOf(GoldPrimary, Color(0xFF38BDF8))
                    )
                )
        )
        Text(
            text = Translator.tr(title),
            color = GoldPrimary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp
        )
    }
}

@Composable
fun SocialAccountItem(platform: String, status: String, isLinked: Boolean, icon: ImageVector, iconColor: Color, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141C27))
            .border(1.dp, if (isLinked) GoldPrimary.copy(alpha = 0.4f) else Color(0xFF222222), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isLinked) iconColor.copy(alpha = 0.15f) else Color(0xFF222222)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (isLinked) iconColor else Color.Gray, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(platform, color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(status, color = if (isLinked) GoldPrimary else Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
        }
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(
                if (isLinked) Translator.tr("إلغاء الربط") else Translator.tr("ربط الحساب"),
                color = if (isLinked) Color(0xFFE53935) else GoldPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun OfficialAccountItem(
    channel: OfficialChannelInfo,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    showDevActions: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: (() -> Unit)? = null
) {
    val (icon, brandColor) = when (channel.id) {
        "website" -> Icons.Default.Language to Color(0xFF00E5FF)
        "group" -> Icons.Default.Groups to Color(0xFF10B981)
        "youtube" -> Icons.Default.OndemandVideo to Color(0xFFFF0000)
        "facebook" -> Icons.Default.ThumbUp to Color(0xFF1877F2)
        "instagram" -> Icons.Default.CameraAlt to Color(0xFFE1306C)
        "threads" -> Icons.Default.AlternateEmail to Color(0xFFE2E8F0)
        "tiktok" -> Icons.Default.MusicVideo to Color(0xFF00F2FE)
        else -> Icons.Default.Public to GoldPrimary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141C27)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(brandColor.copy(alpha = 0.15f))
                            .border(1.dp, brandColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = brandColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel.displayName,
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    channel.badge,
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            channel.handle,
                            color = GoldPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (showDevActions) {
                        Row {
                            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            }
                            if (onDelete != null) {
                                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                channel.description,
                color = Color.LightGray,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1.4f).height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("زيارة القناة / الصفحة", color = DeepSlate, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f).height(38.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نسخ الرابط", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 🚀 شاشة اختيار نمط الشاشة الرئيسية (Home Screen Style Mode SubScreen)
 */
@Composable
fun HomeScreenStyleSubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentStyle = HomeScreenStyleManager.currentStyle.collectAsState().value
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    SubScreenScaffold(
        title = "نمط الشاشة الرئيسية",
        icon = Icons.Default.DashboardCustomize,
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // بطاقة توضيحية علوية
            Surface(
                color = primaryColor.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "تخصيص تجربة الشاشة الرئيسية",
                            color = primaryColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "اختر المظهر المعماري الذي يناسب طريقة عملك. التبديل فوري وبدون فقدان أي بيانات أو مشاريع.",
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // الخيارات المتاحة
            HomeScreenStyle.entries.forEach { style ->
                val isSelected = currentStyle == style

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HomeScreenStyleManager.setStyle(context, style)
                            Toast.makeText(context, "تم تفعيل ${style.title} بنجاح ✅", Toast.LENGTH_SHORT).show()
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) CardSurface else CardSurface.copy(alpha = 0.6f)
                    ),
                    border = BorderStroke(
                        if (isSelected) 1.6.dp else 1.dp,
                        if (isSelected) primaryColor else primaryColor.copy(alpha = 0.2f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) primaryColor.copy(alpha = 0.2f) else DeepSlate,
                                    border = BorderStroke(1.dp, if (isSelected) primaryColor else Color(0xFF1E293B))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(style.iconEmoji, fontSize = 20.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = style.title,
                                        color = TextPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Surface(
                                        color = if (isSelected) primaryColor.copy(alpha = 0.18f) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = style.badge,
                                            color = if (isSelected) primaryColor else TextSecondary,
                                            fontFamily = CairoFont,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // مؤشر الاختيار الدائري
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) primaryColor else Color.Transparent)
                                    .border(1.5.dp, if (isSelected) primaryColor else TextSecondary.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = style.subtitle,
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp
                        )

                        if (isSelected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Stars, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "النمط النشط والمعتمد حالياً في الواجهة",
                                    color = primaryColor,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🎬 شاشة تخصيص وتحديد أنميشن وحركات التنقل بالكامل (Transition Animations Sub-Screen)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionAnimationsSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    val currentAnim by AppAnimationManager.currentAnimation.collectAsState()
    val currentSpeed by AppAnimationManager.currentSpeed.collectAsState()

    // حالة محاكي الحركة التفاعلي المباشر (Live Motion Simulator)
    var simulatorStateIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "أنميشن وحركات التنقل",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = primaryColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Motion ✦",
                                color = primaryColor,
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        },
        containerColor = qabasBackground()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. محاكي المعاينة الحية المباشرة (Live Interactive Motion Preview) ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.2.dp, UiBrush.horizontalGradient(listOf(primaryColor, Color(0xFFA855F7))))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesomeMotion, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "محاكي المعاينة الحية للحركة",
                                    color = TextPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "شاهد أثر الحركة الحالية قبل تطبيقها على الشاشات",
                                    color = TextSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFA855F7).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${currentSpeed.badge} • ${currentAnim.iconEmoji}",
                                color = Color(0xFFA855F7),
                                fontFamily = RobotoMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // شاشة العرض التفاعلية داخل المحاكي
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DeepSlate)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = simulatorStateIndex,
                            transitionSpec = {
                                AppAnimationManager.createTransitionSpec(
                                    initialState = if (initialState == 0) AppState.HOME else AppState.QURAN_HUB,
                                    targetState = if (targetState == 0) AppState.HOME else AppState.QURAN_HUB
                                )
                            },
                            label = "SimulatorTransition"
                        ) { stateIdx ->
                            if (stateIdx == 0) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = primaryColor, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("الشاشة الرئيسية (المركز الفضائي)", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("مصفوفة الإنتاج والوحيين الذكية", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("مِشكاة الهُدى والقرآن الكريم", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("المصحف المرتل وتدبر الآيات", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { simulatorStateIndex = if (simulatorStateIndex == 0) 1 else 0 },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "اختبار تأثير الحركة الآن ⚡",
                            color = DeepSlate,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            // ── 2. محدد سرعة وتوقيت الأنميشن (Speed & Timing) ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "سرعة وتوقيت حركة الشاشات",
                    color = primaryColor,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimationSpeedOption.entries.forEach { speedOpt ->
                        val isSpeedSel = currentSpeed == speedOpt
                        Surface(
                            onClick = {
                                AppAnimationManager.setSpeed(context, speedOpt)
                                Toast.makeText(context, "تم ضبط سرعة الأنميشن: ${speedOpt.title} ⏱️", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSpeedSel) primaryColor.copy(alpha = 0.18f) else CardSurface,
                            border = BorderStroke(if (isSpeedSel) 1.5.dp else 1.dp, if (isSpeedSel) primaryColor else Color(0xFF2A3040)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = speedOpt.badge,
                                    color = if (isSpeedSel) primaryColor else TextSecondary,
                                    fontFamily = RobotoMonoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = speedOpt.title.substringBefore(" ("),
                                    color = if (isSpeedSel) TextPrimary else TextSecondary,
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSpeedSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // ── 3. قائمة أنماط الأنميشن الكاملة الـ 6 ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "اختر نمط الأنميشن المعتمد للتطبيق",
                    color = primaryColor,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                AppTransitionAnimation.entries.forEach { animOption ->
                    val isSelected = currentAnim == animOption

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppAnimationManager.setAnimation(context, animOption)
                                Toast.makeText(context, "تم تفعيل نمط: ${animOption.title} بنجاح ✅", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) CardSurface else CardSurface.copy(alpha = 0.65f)
                        ),
                        border = BorderStroke(
                            if (isSelected) 1.6.dp else 1.dp,
                            if (isSelected) primaryColor else primaryColor.copy(alpha = 0.2f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        modifier = Modifier.size(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) primaryColor.copy(alpha = 0.2f) else DeepSlate,
                                        border = BorderStroke(1.dp, if (isSelected) primaryColor else Color(0xFF1E293B))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(animOption.iconEmoji, fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = animOption.title,
                                            color = TextPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                        Surface(
                                            color = if (isSelected) primaryColor.copy(alpha = 0.18f) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = animOption.badge,
                                                color = if (isSelected) primaryColor else TextSecondary,
                                                fontFamily = CairoFont,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // مؤشر الاختيار
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) primaryColor else Color.Transparent)
                                        .border(1.5.dp, if (isSelected) primaryColor else TextSecondary.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = animOption.subtitle,
                                color = TextSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            if (isSelected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Stars, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "النمط المعتمد والنشط حالياً على جميع شاشات التطبيق",
                                        color = primaryColor,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
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
