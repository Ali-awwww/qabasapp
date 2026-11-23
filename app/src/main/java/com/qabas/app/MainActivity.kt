package com.qabas.app

import io.github.jan.supabase.auth.handleDeeplinks
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.TestTags
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalView
import android.graphics.Bitmap
import java.io.File
import androidx.compose.material.icons.filled.BugReport
import com.qabas.app.BugReportManager
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast

@Immutable
data class Scene(
    val title: String,
    val description: String,
    val durationInSeconds: Int,
    val visualEffect: String = "بدون",
    val tempo: String = "عادي",
    val transitionType: String = "Fade",
    val mediaUrl: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Cold start uses Theme.Khadim.Splash from the manifest; switch back to
        // the normal theme before the window is created.
        setTheme(R.style.Theme_MyApplication)
        super.onCreate(savedInstanceState)
        
        // Restore a previously verified recovery session, then handle any cold-start callback.
        PasswordRecoveryManager.restore(this)
        if (PasswordRecoveryManager.isRecoveryVerified.value) {
            AppNavigator.navigateFromNotification(AppState.FORGOT_PASSWORD)
        }

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        FirebaseAppCheckHelper.configureAppCheck(this, intent)

        // Handle deep links, including Supabase password recovery.
        handleDeepLink(intent)

        // Initialize managers that are fast or critical for UI immediately
        ThemeManager.init(this)
        HomeScreenStyleManager.init(this)
        
        // Launch heavy or IO-dependent initializations in background
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { AppSoundManager.init(this@MainActivity) }
            runCatching { NotificationPreferencesManager.init(this@MainActivity) }
            runCatching { SpatialCameraPreferencesManager.init(this@MainActivity) }
            runCatching { QabasNotificationHelper.initializeChannels(this@MainActivity) }
            
            runCatching {
                StyleBrain.init(this@MainActivity)
                // إصلاح القوة الوهمية 50 عندما السمات فارغة
                StyleBrainLoadFix.repairPersistedEmptyCore(this@MainActivity)
                StyleManager.init(this@MainActivity)
                NotificationPreferencesManager.scheduleAllConfiguredAlarms(this@MainActivity)
            }
            
            // 🛰️ تهيئة واسترجاع رمز الإشعارات (FCM Token) عند الإقلاع
            runCatching {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        android.util.Log.w("MainActivity", "فشل جلب رمز FCM", task.exception)
                        return@addOnCompleteListener
                    }
                    val token = task.result
                    // لا يُسجَّل الرمز نفسه أبداً: رمز FCM يتيح إرسال إشعارات
                    // للجهاز، وقيمته في logcat تتسرب مع أي تقرير أعطال يُشارك.
                    android.util.Log.d("MainActivity", "تم جلب رمز FCM (الطول: ${token.length})")
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            val appLanguage by UserPreferencesManager.appLanguage.collectAsState()

            // Re-derive localized context and layout direction when language changes
            val localizedContext = remember(appLanguage) {
                val locale = java.util.Locale.forLanguageTag(appLanguage)
                java.util.Locale.setDefault(locale)
                val config = android.content.res.Configuration(resources.configuration)
                config.setLocale(locale)
                createConfigurationContext(config)
            }

            val layoutDirection = remember(appLanguage) {
                if (appLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
            }

            val highContrast by UserPreferencesManager.highContrastMode.collectAsState()
            val oledHighContrast by UserPreferencesManager.oledHighContrast.collectAsState()
            val reducedMotion by UserPreferencesManager.reducedMotion.collectAsState()
            var showSplash by rememberSaveable { mutableStateOf(true) }
            val globalTextScale by UserPreferencesManager.globalTextScale.collectAsState()

            val density = androidx.compose.ui.platform.LocalDensity.current
            val customDensity = remember(density, globalTextScale) {
                androidx.compose.ui.unit.Density(
                    density = density.density,
                    fontScale = density.fontScale * globalTextScale
                )
            }

            MyApplicationTheme(highContrastMode = highContrast, oledHighContrast = oledHighContrast) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides layoutDirection,
                    LocalContext provides localizedContext,
                    androidx.compose.ui.platform.LocalDensity provides customDensity,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity
                ) {
                    LaunchedEffect(Unit) {
                        launch(Dispatchers.IO) {
                            runCatching {
                                LeagueService.checkAndProcessWeeklyLeague(this@MainActivity)
                                PointsManager.checkAndAwardDailyLogin(this@MainActivity)
                                // 👑 التحقق السحابي الحصري من رتبة المستخدم وإشعار التهنئة فور الإقلاع
                                AdminGuard.isDashboardAccessAllowed(this@MainActivity)
                            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        }

                        launch {
                            delay(2500)
                            var isFirstLoad = true
                            var lastTransactionCount = 0
                            runCatching {
                                CloudServices.Database.observeAllTransactions().collect { transactions ->
                                    val sharedPrefs =
                                        getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                                    val notifsEnabled =
                                        sharedPrefs.getBoolean("dev_transaction_notifications", false)

                                    if (isFirstLoad) {
                                        lastTransactionCount = transactions.size
                                        isFirstLoad = false
                                    } else if (
                                        transactions.size > lastTransactionCount && notifsEnabled
                                    ) {
                                        val newTx = transactions.firstOrNull()
                                        newTx?.let { tx ->
                                            val productId = tx["productId"] as? String ?: "Unknown"
                                            val price = tx["priceAmount"] as? Double ?: 0.0
                                            NotificationHelper.showNotification(
                                                this@MainActivity,
                                                "مبيعة جديدة! 💰",
                                                "تم بيع $productId بقيمة $${price}"
                                            )
                                        }
                                        lastTransactionCount = transactions.size
                                    } else {
                                        lastTransactionCount = transactions.size
                                    }
                                }
                            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        }
                    }
                    AppStartupPermissionHandler(
                        // زر «مركز الأذونات الكامل» في حوار الأذونات كان
                        // بلا مستدعٍ (القيمة الافتراضية {}): يُغلق الحوار
                        // ولا يفتح شيئاً. الآن ينتقل لمركز الأذونات.
                        onOpenPermissionsHub = { AppNavigator.navigateTo(AppState.PERMISSIONS_HUB) }
                    )
                    
                    val pendingUpdate by UpdateManager.pendingUpdate.collectAsState()

                    GlobalUpdateOverlay(pendingUpdate) {
                        GlobalFeedbackOverlay {
                            Box(modifier = Modifier.fillMaxSize()) {
                                QabasStudioScreen()
                                
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                        .padding(top = 6.dp)
                                        .zIndex(10f),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        com.qabas.app.ui.components.NetworkHUD()
                                com.qabas.app.ui.components.OnlineHUD()
                                    }
                                }

                                // Brand splash overlay: covers the content until it
                                // dismisses itself (~1.4s); the app keeps
                                // initialising underneath.
                                if (showSplash) {
                                    KhadimSplashScreen(
                                        reducedMotion = reducedMotion,
                                        modifier = Modifier.zIndex(20f),
                                        onFinished = { showSplash = false }
                                    )
                                }

                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GlobalFeedbackOverlay(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val view = LocalView.current
        val scope = rememberCoroutineScope()
        
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val accountAuthState by SupabaseRepository.getInstance().authState.collectAsState()
        val isGuestUser = accountAuthState.requiresInteractiveLogin()
        val isRegisteredUser = accountAuthState is AuthState.Authenticated
        val isCameraEnabled by SpatialCameraPreferencesManager.cameraEnabled.collectAsState()

        var showFab by remember { mutableStateOf(prefs.getBoolean("show_floating_debug_fabs", false)) }
        var showReportDialog by remember { mutableStateOf(false) }
        var showCameraChooser by remember { mutableStateOf(false) }
        var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var screenshotFile by remember { mutableStateOf<File?>(null) }
        var pendingCameraFileUri by remember { mutableStateOf<Uri?>(null) }

        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    val bmp = runCatching { context.contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s) } }.getOrNull()
                    withContext(Dispatchers.Main) {
                        if (bmp != null) {
                            val file = BugReportManager.saveBitmapToCache(context, bmp)
                            screenshotBitmap = bmp
                            screenshotFile = file
                            showReportDialog = true
                        }
                    }
                }
            }
        }
        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            pendingCameraFileUri?.let { uri ->
                scope.launch(Dispatchers.IO) {
                    val bmp = runCatching { context.contentResolver.openInputStream(uri)?.use { s -> BitmapFactory.decodeStream(s) } }.getOrNull()
                    withContext(Dispatchers.Main) {
                        if (ok && bmp != null) {
                            val file = BugReportManager.saveBitmapToCache(context, bmp)
                            screenshotBitmap = bmp
                            screenshotFile = file
                            showReportDialog = true
                        }
                    }
                }
            }
        }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val file = File(context.cacheDir, "bug_camera_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                pendingCameraFileUri = uri
                cameraLauncher.launch(uri)
            }
        }
        
        var description by remember { mutableStateOf("") }
        var reportType by remember { mutableStateOf("BUG") }
        var isSubmitting by remember { mutableStateOf(false) }
        var attachedTelemetry by remember { mutableStateOf<PrecisionDiagnosticEngine.DiagnosticTelemetry?>(null) }

        val allReports by BugReportRepository.reports.collectAsState()
        val supabaseAuthUser by SupabaseServices.Auth.currentUser.collectAsState()
        val cloudAuthUser by CloudServices.Auth.currentUser.collectAsState()
        val supabaseUserId = remember(supabaseAuthUser, cloudAuthUser) {
            SupabaseServices.Auth.getCurrentUserId()
        }
        val authenticatedEmail = supabaseAuthUser?.email ?: cloudAuthUser?.email
        val myReports = remember(allReports, supabaseAuthUser, cloudAuthUser, supabaseUserId, authenticatedEmail) {
            BugReportRepository.filterReportsForOwner(
                source = allReports,
                localOwnerId = UserPreferencesManager.getEffectiveUserId(context),
                supabaseUserId = supabaseUserId,
                authenticatedEmail = authenticatedEmail
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(DeepSlate)) {
            content()

            // 🛰️ زر الكاميرا الفضائي العائم لالتقاط لقطات الشاشة وإرسالها للمطور (مزروع في جميع الشاشات وقابل للتحريك لأي زاوية)
            val isDevScreen = BugReportManager.currentScreen.contains("DEVELOPER", ignoreCase = true)
            if (!isGuestUser && isRegisteredUser && isCameraEnabled && !showReportDialog && !isDevScreen) {
                SpatialDraggableDevCameraButton(
                    onScreenshotCaptured = { bitmap, file ->
                        screenshotBitmap = bitmap
                        screenshotFile = file
                        attachedTelemetry = null
                        showReportDialog = true
                    },
                    onOpenDevChat = {
                        AppNavigator.navigateTo(AppState.REQUEST_CHAT)
                    },
                    onOpenMyReports = {
                        AppNavigator.navigateTo(AppState.REQUEST_CHAT)
                    },
                    onOpenDiagnosticReport = { telemetry, bitmap, file ->
                        screenshotBitmap = bitmap
                        screenshotFile = file
                        attachedTelemetry = telemetry
                        reportType = telemetry.detectedProbableIssue.code
                        val formattedTelemetrySummary = "• رصد آلي: ${telemetry.detectedProbableIssue.title}\n• الأبعاد: ${telemetry.widthDp}×${telemetry.heightDp} dp (${telemetry.densityDpi} DPI)\n• الذاكرة: ${telemetry.usedRamMb}/${telemetry.maxRamMb} MB"
                        description = if (description.isBlank()) formattedTelemetrySummary else "$description\n$formattedTelemetrySummary"
                        showReportDialog = true
                    }
                )
            }

            if (showReportDialog && screenshotBitmap != null) {
                val theme = QabasTheme.current
                Dialog(
                    onDismissRequest = { if (!isSubmitting) showReportDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = theme.cardSurface,
                        shadowElevation = if (theme.isDark) 4.dp else 12.dp,
                        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(18.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.BugReport, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                                    Text(
                                        "إبلاغ أو اقتراح تحسين",
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Surface(
                                    onClick = {
                                        AppSoundManager.playClick()
                                        showReportDialog = false
                                        AppNavigator.navigateTo(AppState.REQUEST_CHAT)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = theme.primary.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                                        Text(
                                            if (myReports.isNotEmpty()) "طلباتي (${myReports.size})" else "طلباتي السابقة",
                                            color = theme.primary,
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(176.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = theme.secondarySurface),
                                border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.5f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = screenshotBitmap,
                                        contentDescription = "معاينة اللقطة",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                    attachedTelemetry?.let { tel ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = theme.cardSurface.copy(alpha = 0.92f),
                                            border = BorderStroke(0.5.dp, theme.primary.copy(alpha = 0.5f)),
                                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Troubleshoot, contentDescription = null, tint = theme.primary, modifier = Modifier.size(13.dp))
                                                Text(
                                                    text = "تشخيص: ${tel.detectedProbableIssue.title}",
                                                    color = theme.primary,
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "BUG" to "خلل عام",
                                    "UI_OVERFLOW" to "تداخل عناصر",
                                    "IMPROVEMENT" to "تحسين",
                                    "CONTENT" to "محتوى"
                                ).forEach { (type, label) ->
                                    val selected = reportType == type
                                    Surface(
                                        onClick = {
                                            AppSoundManager.playToggle()
                                            reportType = type
                                            if (type == "UI_OVERFLOW" && description.isBlank()) {
                                                description = "يوجد تداخل بين عناصر الواجهة في هذه الشاشة."
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) theme.primary else theme.secondarySurface,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            label,
                                            color = if (selected) theme.onPrimary else theme.textPrimary,
                                            fontSize = 11.sp,
                                            fontFamily = CairoFont,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "تشخيص النظام" to "DIAGNOSE_NOW",
                                    "تداخل نصوص" to "يوجد تداخل وازدحام بين النصوص والأزرار في هذه الشاشة.",
                                    "عناصر مقصوصة" to "توجد عناصر أو نصوص مقصوصة وغير كاملة الظهور."
                                ).forEach { (chipTitle, chipAction) ->
                                    val isDiagnosticChip = chipAction == "DIAGNOSE_NOW"
                                    Surface(
                                        onClick = {
                                            AppSoundManager.playClick()
                                            if (isDiagnosticChip) {
                                                val telemetry = PrecisionDiagnosticEngine.harvestTelemetry(
                                                    context = context,
                                                    screenName = BugReportManager.currentScreen,
                                                    widthDp = configuration.screenWidthDp.toFloat(),
                                                    heightDp = configuration.screenHeightDp.toFloat()
                                                )
                                                attachedTelemetry = telemetry
                                                reportType = telemetry.detectedProbableIssue.code
                                                val summary = "• رصد آلي: ${telemetry.detectedProbableIssue.title}\n• الأبعاد: ${telemetry.widthDp}×${telemetry.heightDp} dp (${telemetry.densityDpi} DPI)\n• الذاكرة: ${telemetry.usedRamMb}/${telemetry.maxRamMb} MB"
                                                description = if (description.isBlank()) summary else "$description\n$summary"
                                                Toast.makeText(context, "تم إجراء التشخيص وإرفاق التفاصيل", Toast.LENGTH_SHORT).show()
                                            } else {
                                                if (chipTitle.contains("تداخل")) {
                                                    reportType = "UI_OVERFLOW"
                                                }
                                                description = if (description.isBlank()) chipAction else "$description\n• $chipAction"
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDiagnosticChip) theme.primary.copy(alpha = 0.14f) else theme.secondarySurface,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            chipTitle,
                                            color = if (isDiagnosticChip) theme.primary else theme.textSecondary,
                                            fontSize = 10.sp,
                                            fontFamily = CairoFont,
                                            fontWeight = if (isDiagnosticChip) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { if (it.length <= 500) description = it },
                                    placeholder = { Text("صف المشكلة أو الاقتراح بالتفصيل هنا...", color = theme.textSecondary, fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = CairoFont, fontSize = 13.sp, color = theme.textPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = theme.primary,
                                        unfocusedBorderColor = theme.separator.copy(alpha = 0.6f)
                                    )
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Text(
                                        "${description.length}/500",
                                        color = if (description.length > 450) theme.destructive else theme.textSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = CairoFont
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        AppSoundManager.playClick()
                                        showReportDialog = false
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        if (description.isBlank()) return@Button
                                        isSubmitting = true
                                        scope.launch {
                                            val finalDescription = if (attachedTelemetry != null) {
                                                PrecisionDiagnosticEngine.formatDiagnosticMarkdown(attachedTelemetry!!, description)
                                            } else {
                                                description
                                            }
                                            val result = BugReportRepository.submitReport(
                                                context = context,
                                                screenName = BugReportManager.currentScreen,
                                                description = finalDescription,
                                                reportType = reportType,
                                                screenshotBitmap = screenshotBitmap,
                                                screenshotFile = screenshotFile
                                            )
                                            isSubmitting = false
                                            AppSoundManager.playSuccess()
                                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                            showReportDialog = false
                                            description = ""
                                            attachedTelemetry = null
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isSubmitting
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = theme.onPrimary, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إرسال", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showCameraChooser) {
                val theme = QabasTheme.current
                Dialog(onDismissRequest = { showCameraChooser = false }) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = theme.cardSurface,
                        border = BorderStroke(0.5.dp, theme.separator.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("إرفاق صورة للبلاغ", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.45f))
                            TextButton(
                                onClick = {
                                    showCameraChooser = false
                                    showFab = false
                                    scope.launch {
                                        delay(150)
                                        val bitmap = BugReportManager.captureScreenshot(view)
                                        if (bitmap != null) {
                                            val file = BugReportManager.saveBitmapToCache(context, bitmap)
                                            screenshotBitmap = bitmap
                                            screenshotFile = file
                                            showReportDialog = true
                                        }
                                        showFab = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("لقطة شاشة", color = theme.primary, fontFamily = CairoFont) }
                            TextButton(
                                onClick = {
                                    showCameraChooser = false
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        val file = File(context.cacheDir, "bug_camera_${System.currentTimeMillis()}.jpg")
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        pendingCameraFileUri = uri
                                        cameraLauncher.launch(uri)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("التقاط بالكاميرا", color = theme.primary, fontFamily = CairoFont) }
                            TextButton(
                                onClick = {
                                    showCameraChooser = false
                                    galleryLauncher.launch("image/*")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("اختيار من المعرض", color = theme.primary, fontFamily = CairoFont) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GlobalUpdateOverlay(
        update: UpdateManager.UpdateInfo?,
        content: @Composable () -> Unit
    ) {
        val context = LocalContext.current
        val downloadProgress by UpdateManager.downloadProgress.collectAsState()
        val showUpdateErrorDialog by UpdateTelemetryManager.showErrorDialog.collectAsState()
        val latestFailureReport by UpdateTelemetryManager.latestFailureReport.collectAsState()
        
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            if (update != null && !update.isForce && !UpdateManager.isUpdateDismissedForSession) {
                UpdateNotificationDialog(
                    update = update,
                    downloadProgress = downloadProgress,
                    onDismiss = { UpdateManager.dismissUpdate() },
                    onInstallDelta = { UpdateDownloadService.start(context, update, forceFull = false) },
                    onInstallFull = { UpdateDownloadService.start(context, update, forceFull = true) }
                )
            }

            if (showUpdateErrorDialog && latestFailureReport != null) {
                UpdateErrorReportDialog(
                    report = latestFailureReport!!,
                    onDismiss = { UpdateTelemetryManager.dismissErrorDialog() },
                    onFallbackFullDownload = {
                        UpdateTelemetryManager.dismissErrorDialog()
                        val updateInfo = update ?: UpdateManager.pendingUpdate.value
                        if (updateInfo != null) {
                            UpdateDownloadService.start(context, updateInfo, forceFull = true)
                        } else {
                            val repo = UpdateManager.getEffectiveRepo(context)
                            val url = "https://github.com/$repo/releases/latest"
                            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(browserIntent)
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
        handleNotificationNavigation(intent)
        // حدّث نية النشاط حتى يعالج onResume الأحدث لا الأصلية القديمة
        setIntent(intent)
        
        // معالجة نية التثبيت المباشرة (إن وجد اسم ونسخة دقيقة)
        if (intent.action == UpdateInstallReceiver.ACTION_INSTALL) {
            val name = intent.getStringExtra(UpdateInstallReceiver.EXTRA_VERSION_NAME) ?: ""
            val code = intent.getIntExtra(UpdateInstallReceiver.EXTRA_VERSION_CODE, -1)
            val size = intent.getLongExtra(UpdateInstallReceiver.EXTRA_APK_SIZE, 0L)
            if (name.isNotBlank() && code > 0) {
                UpdateManager.setPendingInstall(this, name, code, size)
                UpdateManager.attemptPendingInstall(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UpdateManager.onAppForeground()
        // فحص تلقائي عند العودة للتطبيق — محكوم بمدة الفحص المختارة (ساعتان كحد أدنى)
        runCatching { DownloadsHubManager.checkAndStartAutoUpdate(this, force = false) }
        handleNotificationNavigation(intent)
        // نية «تثبيت الآن» من الإشعار تُستهلك مرة واحدة فقط
        if (handleInstallIntent(intent)) {
            UpdateManager.attemptPendingInstall(this)
        } else {
            UpdateManager.maybeResumePendingInstall(this)
        }
    }

    override fun onPause() {
        UpdateManager.onAppBackground()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        // تنظيف حالة التحديثات القديمة وإعادة إشعار التثبيت إن كانت الحزمة جاهزة
        UpdateManager.reconcilePendingInstall(this)
    }

    /**
     * معالجة وجهة التنقّل من الإشعارات (navigate_to / target_screen).
     * كانت تُكتب هذه الحقول في النوايا ولا يقرأها أحد — فكانت وعود تنقّل معطّلة.
     * تُستهلك مرة واحدة وتُطلب عبر AppNavigator (تعمل مع الإقلاع البارد والاستئناف).
     */
    private fun handleNotificationNavigation(intent: android.content.Intent) {
        val target = intent.getStringExtra("navigate_to")
            ?: intent.getStringExtra("target_screen")
            ?: return
        // استهلاك الحقول — لن يُعالج الطلب مرة أخرى
        intent.removeExtra("navigate_to")
        intent.removeExtra("target_screen")
        val appState = when (target.lowercase()) {
            "prayer_times" -> AppState.PRAYER_TIMES
            "notifications" -> AppState.NOTIFICATIONS
            "quran_hub" -> AppState.MUSHAF_READER
            "hadith_studio" -> AppState.HADITH_STUDIO
            "azkar" -> AppState.AZKAR
            "rewards" -> AppState.REWARDS
            "downloads", "download_manager" -> AppState.DOWNLOAD_MANAGER
            "settings" -> AppState.SETTINGS
            "request_chat", "developer_contact" -> AppState.REQUEST_CHAT
            else -> runCatching { AppState.valueOf(target.uppercase()) }.getOrNull()
        } ?: return
        AppNavigator.navigateFromNotification(appState)
    }

    /** معالجة نية تثبيت التحديث الواردة من إشعار «تثبيت الآن».
     * تُستهلك النية (تُمسح) بعد القراءة حتى لا يُعاد فتح المثبّت
     * عند كل onResume. تعيد true فقط عند أول قراءة نية تثبيت.
     */
    private fun handleInstallIntent(intent: android.content.Intent): Boolean {
        if (intent.action != UpdateInstallReceiver.ACTION_INSTALL) return false
        val versionName = intent.getStringExtra(UpdateInstallReceiver.EXTRA_VERSION_NAME).orEmpty()
        val versionCode = intent.getIntExtra(UpdateInstallReceiver.EXTRA_VERSION_CODE, -1)
        val apkSize = intent.getLongExtra(UpdateInstallReceiver.EXTRA_APK_SIZE, 0L)
        // استهلاك النية — لن تُعالج مرة أخرى
        intent.action = null
        if (versionName.isNotBlank() && versionCode > 0) {
            UpdateManager.setPendingInstall(this, versionName, versionCode, apkSize)
            return true
        }
        return false
    }

    private fun handleDeepLink(intent: android.content.Intent) {
        val uri = intent.data
        // رابط الموقع الرسمي: كان المضيف في البيان (نطاق run.app
        // التجريبي) لا يطابق مضيف QabasWebConfig (github.io) فلا يصل
        // الرابط إلى هنا أبداً؛ وحتى عند المطابقة كان تنبيهاً فقط
        // والمسار يُرمى. الآن: المضيفان (الرسمي والقديم) يفتحان
        // التطبيق على الرئيسية — فالإقلاع البارد يهبط عليها أصلاً،
        // والرابط الدافئ يعيد المستخدم إليها من أي شاشة.
        // (لا توجيه حسب المسار: الموقع بلا مخطط مسارات موثّق للتطبيق.)
        if (uri != null && (uri.host == QabasWebConfig.OFFICIAL_WEB_HOST || uri.host == QabasWebConfig.LEGACY_WEB_HOST)) {
            android.widget.Toast.makeText(this, "مرحباً بك في تطبيق خادم المسلم عبر الموقع الرسمي 🌐", android.widget.Toast.LENGTH_SHORT).show()
            AppNavigator.navigateTo(AppState.HOME)
        }

        if (uri != null &&
            uri.scheme == SupabaseConfig.PASSWORD_RESET_SCHEME &&
            uri.host == SupabaseConfig.PASSWORD_RESET_HOST
        ) {
            // ق85: qabas://auth كان يُصنَّف استعادةً دائماً لمجرد تطابق
            // المضيف — فرابط تأكيد البريد الواصل للتطبيق كان يهبط على
            // شاشة «نسيت كلمة المرور» ويُسلِّح حالة الاستعادة؛ والرابط
            // الضمني (#access_token) كان يُرفض برسالة «منتهي الصلاحية»
            // كاذبة قبل أي تبادل. التصنيف الآن بنوع الرابط الصريح أو
            // بنية استعادة مسجلة عند الطلب؛ وما عداه تأكيد بريد.
            val isRecovery = classifyQabasAuthLink(
                uri.toString(),
                PasswordRecoveryManager.isRecoveryRequested(this)
            )
            val callbackError = uri.getQueryParameter("error_description")
                ?: uri.getQueryParameter("error")
            if (!callbackError.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    if (isRecovery) "تعذّر التحقق من رابط الاستعادة. أرسل رابطاً جديداً."
                    else "تعذّر التحقق من رابط تأكيد البريد. أعد إرسال رسالة التحقق.",
                    Toast.LENGTH_LONG
                ).show()
                if (isRecovery) PasswordRecoveryManager.clearRequested(this)
                return
            }

            if (!isRecovery) {
                // رابط تأكيد بريد: تبادل الرمز (أو الرمز الضمني في
                // الشظية) لجلسة، ثم تحديث حالة التأكيد والعودة للرئيسية.
                val code = uri.getQueryParameter("code")
                val hasImplicitToken = uri.fragment?.contains("access_token=") == true
                if (code.isNullOrBlank() && !hasImplicitToken) {
                    Toast.makeText(this, "رابط التأكيد غير مكتمل أو منتهي الصلاحية. أعد إرسال رسالة التحقق من الإعدادات.", Toast.LENGTH_LONG).show()
                    return
                }
                SupabaseConfig.client.handleDeeplinks(
                    intent = intent,
                    onSessionSuccess = {
                        lifecycleScope.launch {
                            SupabaseServices.Auth.refreshEmailConfirmationState()
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "تم تأكيد بريدك — المزامنة السحابية مفعّلة الآن",
                                    Toast.LENGTH_LONG
                                ).show()
                                AppNavigator.navigateTo(AppState.HOME)
                            }
                        }
                    },
                    onError = { error ->
                        android.util.Log.e("EmailConfirmation", "Supabase confirmation callback failed", error)
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "انتهت صلاحية رابط التأكيد أو تعذّر التحقق منه. أعد إرسال رسالة تحقق جديدة.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
                return
            }

            // رابط استعادة: الرمز في الاستعلام (PKCE) أو الشظية
            // (التدفق الضمني) — غيابهما معاً رابط غير مكتمل، لا
            // استدعاء تبادل بلا شيء يتبادله.
            val recoveryCode = uri.getQueryParameter("code")
            val hasRecoveryToken = uri.fragment?.contains("access_token=") == true
            if (recoveryCode.isNullOrBlank() && !hasRecoveryToken) {
                Toast.makeText(this, "رابط الاستعادة غير مكتمل أو منتهي الصلاحية. أرسل رابطاً جديداً.", Toast.LENGTH_LONG).show()
                PasswordRecoveryManager.clearRequested(this)
                return
            }

            PasswordRecoveryManager.begin()
            SupabaseConfig.client.handleDeeplinks(
                intent = intent,
                onSessionSuccess = {
                    PasswordRecoveryManager.markVerified(this@MainActivity)
                    PasswordRecoveryManager.clearRequested(this@MainActivity)
                    AppNavigator.navigateFromNotification(AppState.FORGOT_PASSWORD)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "تم التحقق من رابط الاستعادة. عيّن كلمة مرور جديدة.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onError = { error ->
                    PasswordRecoveryManager.clear(this@MainActivity)
                    PasswordRecoveryManager.clearRequested(this@MainActivity)
                    android.util.Log.e("PasswordRecovery", "Supabase recovery callback failed", error)
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "انتهت صلاحية رابط الاستعادة أو تعذّر التحقق منه. أرسل رابطاً جديداً.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
            return
        }

        val result = KeyDeepLinkHandler.parseIntent(intent) ?: return

        when (result.action) {
            "import" -> {
                val (success, msg) = KeyDeepLinkHandler.applyImportedKeys(this, result.keys)
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
                SystemLogsManager.addLog("DEEP_LINK", msg, if (success) androidx.compose.ui.graphics.Color(0xFF10B981) else androidx.compose.ui.graphics.Color(0xFFEF4444))
            }
            "sync" -> {
                when (result.syncDirection) {
                    "push" -> KeySyncService.pushToCloud(this) { _, msg ->
                        runOnUiThread { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show() }
                    }
                    "pull" -> KeySyncService.pullFromCloud(this) { _, msg ->
                        runOnUiThread { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show() }
                    }
                    else -> KeySyncService.syncBidirectional(this) { _, msg ->
                        runOnUiThread { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show() }
                    }
                }
            }
            "validate" -> {
                // كان تنبيهاً فقط («جاري التحقق...») بلا أي تحقق.
                // التحقق الحقيقي: فحص المفاتيح الأربعة المضبوطة فعلاً
                // عبر ApiKeyValidator (HTTP حي لكل مزود) وعرض النتيجة.
                lifecycleScope.launch {
                    val specs = listOf(
                        "gemini" to KeyVault.gemini,
                        "groq" to KeyVault.groq,
                        "openrouter" to KeyVault.openrouter,
                        "pixabay" to KeyVault.pixabay
                    ).filter { it.second.isNotBlank() }
                    if (specs.isEmpty()) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "لا مفاتيح مضبوطة للتحقق منها — أضف مفتاحاً أولاً.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    var valid = 0
                    specs.forEach { (service, key) ->
                        runCatching {
                            if (ApiKeyValidator.validateKey(this@MainActivity, service, key).isValid) valid++
                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    }
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "التحقق اكتمل: $valid من ${specs.size} مفتاحاً صالحاً ✅",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

private data class AppleNavItem(
    val route: AppState,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelKey: String
)

@Composable
fun QabasBottomNavigation(
    currentRoute: AppState,
    onNavigate: (AppState) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val reducedMotion by UserPreferencesManager.reducedMotion.collectAsState()
    val navItems = remember {
        listOf(
            AppleNavItem(AppState.HOME, Icons.Filled.Home, Icons.Outlined.Home, "الرئيسية"),
            AppleNavItem(AppState.HASANAT_HUB, Icons.Filled.AutoStories, Icons.Outlined.AutoStories, "المِشكاة"),
            AppleNavItem(AppState.REELS, Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "ريلز"),
            AppleNavItem(AppState.VIBE_CODING, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "فايب كودينج"),
            AppleNavItem(AppState.SETTINGS, Icons.Filled.Settings, Icons.Outlined.Settings, "الإعدادات")
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.cardSurface.copy(alpha = if (theme.isDark) 0.94f else 0.96f),
        shadowElevation = if (theme.isDark) 0.dp else 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = theme.separator.copy(alpha = 0.55f)
            )
            NavigationBar(
                containerColor = Color.Transparent,
                contentColor = theme.primary,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 62.dp)
            ) {
                navItems.forEach { item ->
                    val label = Translator.tr(item.labelKey)
                    val isSelected = currentRoute == item.route
                    val iconScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isSelected && !reducedMotion) 1.08f else 1.0f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        ),
                        label = "nav_icon_scale"
                    )

                    NavigationBarItem(
                        modifier = Modifier.testTag(TestTags.NAV_ITEM_PREFIX + item.route.name),
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = label,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            // ق103: الاهتزاز يأتي من playTabRouteSound
                            // (withHaptic=true)؛ كان performHapticFeedback
                            // هنا يهزّ الجهاز مرة ثانية مع كل نقرة تبويب.
                            AppSoundManager.playTabRouteSound(item.route)
                            onNavigate(item.route)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = theme.primary,
                            unselectedIconColor = theme.textSecondary,
                            selectedTextColor = theme.primary,
                            unselectedTextColor = theme.textSecondary,
                            indicatorColor = theme.primary.copy(alpha = if (theme.isDark) 0.16f else 0.10f)
                        )
                    )
                }
            }
        }
    }
}

enum class AppState {
    PREMIUM_UPGRADE, DATA_LOADING, ONBOARDING, LOGIN, REGISTER, FORGOT_PASSWORD,
    DEVELOPER_DASHBOARD, DEVELOPER_KEY_STATUS_HUB, HOME, PROJECTS, INPUT, UNDERSTANDING, RESOURCES, PROCESSING, REVIEW,
    ADVANCED_EDIT, PAYMENT, SAVE_SHARE, SAVE_PROJECT, API_DOCS, SETTINGS, TELEPROMPTER,
    AUDIO_LIBRARY, AI_ASSISTANT, YOUTUBE_STUDIO, LEADERBOARD, PROFILE,
    REQUEST_CHAT, NOTIFICATIONS, TASTE_PROFILE, SMART_DIRECTOR, REWARDS,
    KNOWLEDGE_HUB, CONTENT_GUARD, HASANAT_HUB, MUSHAF_READER, HADITH_STUDIO, ENTERPRISE_PORTAL, VIDEO_STYLE_CLONER,
    PHOTO_STUDIO, STYLE_SELECTION, CREATE_STYLE_OBJECT, DIRECTOR_CHAT,
    QASAS, TRANSCRIPT_EDIT, CLIP_EXTRACTOR, CAMERA_CAPTURE, SCHOLAR_BIOGRAPHIES, SCHOLAR_DETAIL,
    AZKAR, ISLAMIC_LIBRARY, PRAYER_TIMES, STUDIO_GATEWAY, PRO_EDITOR, SOCIAL_STATS,
    ABOUT_US, MONEY_PRINTER_TURBO, VOICE_STUDIO, UNIFIED_MASTER_STUDIO, MEDIA_BROWSER,
    PERMISSIONS_HUB, CRASH_LOGS_HUB, REELS, RULES, AI_STUDIO_AGENT, AGENT_TEAM_CONFIG, SAVED_ITEMS,
    DOWNLOAD_MANAGER, UPDATE_HISTORY, STUDIO_AGENT, MY_KEYS, STUDIO_LIBRARY, VIBE_CODING, ADVANCED_KEYS
}

@Immutable
data class ProjectState(
    val selectedRatio: String = "9:16",
    val inputText: String = "",
    val contentType: String = Translator.tr("قصة تاريخية"),
    val contentTone: String = Translator.tr("ملحمي"),
    val targetPlatform: String = "TikTok",
    val marketingGoal: String = Translator.tr("تفاعل (Engagement)"),
    val callToAction: String = Translator.tr("متابعة للمزيد"),
    val videoStyleAnalysis: VideoStyleAnalysis? = null,
    val mediaResources: List<MediaResource> = emptyList(),
    val currentProjectId: String? = null,
    val projectTitle: String = Translator.tr("مشروع جديد"),
    val appState: AppState = AppState.DATA_LOADING,
    val videoDuration: String = Translator.tr("30 ثانية"),
    val editingStyle: String = Translator.tr("أسلوب 3nvus / نيون داكن"),
    val voiceOver: String = Translator.tr("عميق"),
    val musicVibe: String = Translator.tr("ملحمية"),
    val styleDescription: String = "",
    val selectedTemplate: String = Translator.tr("تلقائي"),
    val customTemplate: Template? = null,
    val videoQuality: String = Translator.tr("عالية الدقة 1080p"),
    val ambientSound: String = Translator.tr("تلقائي"),
    val libraryScenes: List<Scene> = emptyList(),
    val scenesToProcess: List<Scene>? = null,
    val projects: List<ProjectService.Project> = emptyList(),
    val finalVideoPath: String = "",
    val selectedRequestId: String? = null,
    val selectedScholar: ScholarBiography? = null,
    val sourceType: String = "idea",
    val sourceUris: List<String> = emptyList(),
    val sourceLabel: String? = null,
    val selectedVerseText: String? = null,
    val selectedSurahName: String? = null,
    val selectedVerseNumber: Int? = null,
    // أهداف التنقّل العميق (الدفعة 5 — الجزء C): لقطة واحدة تُستهلك عند
    // فتح الوجهة ثم تُمسح، فلا تُعاد القفزة عند دخول القسم لاحقاً.
    // عمداً خارج ترميز ProjectWorkspaceCodec (مثل selectedScholar).
    val mushafInitialSurah: Int? = null,
    val mushafInitialAyah: Int? = null,
    val mushafInitialPage: Int? = null,
    val libraryInitialBookId: String? = null,
    val libraryInitialHadithId: String? = null,
    val deepLinkReelId: String? = null,
    val deepLinkCreatorId: String? = null,
    val deepLinkCreatorName: String? = null
)

class ProjectViewModel(private val context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
    private val projectService = ProjectService(context)

    private val _state = MutableStateFlow(
        ProjectState(
            appState = AppState.DATA_LOADING,
            selectedRatio = prefs.getString("ratio", "9:16") ?: "9:16",
            inputText = prefs.getString("input", "") ?: "",
            videoDuration = prefs.getString("duration", Translator.tr("30 ثانية"))
                ?: Translator.tr("30 ثانية"),
            editingStyle = prefs.getString("style", Translator.tr("أسلوب 3nvus / نيون داكن"))
                ?: Translator.tr("أسلوب 3nvus / نيون داكن"),
            voiceOver = prefs.getString("voice", Translator.tr("عميق")) ?: Translator.tr("عميق"),
            musicVibe = prefs.getString("music", Translator.tr("ملحمية")) ?: Translator.tr("ملحمية"),
            styleDescription = prefs.getString("styleDesc", "") ?: "",
            selectedTemplate = prefs.getString("template", Translator.tr("تلقائي"))
                ?: Translator.tr("تلقائي"),
            videoQuality = prefs.getString("quality", Translator.tr("عالية الدقة 1080p"))
                ?: Translator.tr("عالية الدقة 1080p"),
            ambientSound = prefs.getString("ambient", Translator.tr("تلقائي"))
                ?: Translator.tr("تلقائي"),
        )
    )
    val state: StateFlow<ProjectState> = _state.asStateFlow()

    private var prefsJob: Job? = null
    private var dbJob: Job? = null

    init {
        WorkspaceAutoSaveManager.init(context)
        WorkspaceAutoSaveManager.startPeriodicAiAutoSave { _state.value }
        loadProjects()
        
        viewModelScope.launch {
            var previousStatus: ConnectivityObserver.Status? = null
            UserPreferencesManager.networkStatus.collect { status ->
                if (previousStatus != status) {
                    if (status == ConnectivityObserver.Status.Available) {
                        if (previousStatus != null) {
                            Toast.makeText(context, "تم استعادة الاتصال السحابي 🚀", Toast.LENGTH_SHORT).show()
                        }
                        projectService.syncPendingProjects()
                        loadProjects()
                    } else if (status == ConnectivityObserver.Status.Lost || status == ConnectivityObserver.Status.Unavailable) {
                        Toast.makeText(context, "انقطع الاتصال.. تم تفعيل الحفظ المحلي التلقائي 📡", Toast.LENGTH_SHORT).show()
                    }
                }
                previousStatus = status
            }
        }
    }

    fun updateState(update: ProjectState.() -> ProjectState) {
        _state.value = _state.value.update()
        WorkspaceAutoSaveManager.scheduleAiStudioSave(_state.value)
        prefsJob?.cancel()
        prefsJob = viewModelScope.launch {
            delay(350)
            val s = _state.value
            withContext(Dispatchers.IO) {
                prefs.edit()
                    .putString("ratio", s.selectedRatio)
                    .putString("input", s.inputText)
                    .putString("duration", s.videoDuration)
                    .putString("style", s.editingStyle)
                    .putString("voice", s.voiceOver)
                    .putString("music", s.musicVibe)
                    .putString("styleDesc", s.styleDescription)
                    .putString("template", s.selectedTemplate)
                    .putString("quality", s.videoQuality)
                    .putString("ambient", s.ambientSound)
                    .apply()
            }
        }
        scheduleSaveProjectToDb()
    }

    private fun scheduleSaveProjectToDb() {
        dbJob?.cancel()
        dbJob = viewModelScope.launch {
            delay(600)
            saveProjectToDb()
        }
    }

    private suspend fun saveProjectToDb() {
        val s = _state.value
        val id = s.currentProjectId ?: return
        withContext(Dispatchers.IO) {
            val status = when (s.appState) {
                AppState.PAYMENT -> Translator.tr("مسودة")
                AppState.SAVE_SHARE -> Translator.tr("مدفوع")
                AppState.SAVE_PROJECT -> Translator.tr("مكتمل")
                else -> Translator.tr("مسودة")
            }
            projectService.saveProject(
                ProjectService.Project(
                    id = id,
                    title = s.projectTitle,
                    idea = s.inputText,
                    analysis = "",
                    resources = s.mediaResources.size.toString(),
                    settings = "${s.selectedRatio}, ${s.videoDuration}, ${s.editingStyle}, ${s.selectedTemplate}",
                    script = s.libraryScenes.size.toString(),
                    finalVideoPath = s.finalVideoPath,
                    cost = 0.0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    status = status,
                    lastScreen = s.appState.name,
                    workspaceJson = ProjectWorkspaceCodec.encode(s)
                )
            )
        }
        loadProjects()
    }

    fun createProject(initialText: String = "") {
        updateState {
            copy(
                currentProjectId = java.util.UUID.randomUUID().toString(),
                projectTitle = Translator.tr("مشروع ") + (1000..9999).random(),
                inputText = initialText,
                mediaResources = emptyList(),
                libraryScenes = emptyList(),
                finalVideoPath = "",
                appState = AppState.INPUT
            )
        }
    }

    fun openProject(project: ProjectService.Project) {
        updateState {
            val restored = ProjectWorkspaceCodec.decode(project.workspaceJson)
            if (restored != null) {
                restored.applyTo(this).copy(
                    currentProjectId = project.id,
                    projectTitle = project.title.ifBlank { restored.projectTitle },
                    inputText = restored.inputText.ifBlank { project.idea },
                    finalVideoPath = restored.finalVideoPath.ifBlank { project.finalVideoPath }
                )
            } else {
                copy(
                    currentProjectId = project.id,
                    projectTitle = project.title,
                    inputText = project.idea,
                    finalVideoPath = project.finalVideoPath,
                    appState = try {
                        AppState.valueOf(project.lastScreen)
                    } catch (_: Exception) {
                        AppState.INPUT
                    }
                )
            }
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                projectService.deleteProject(id)
            }
            loadProjects()
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                projectService.getAllProjects(syncCloud = true)
            }
            _state.value = _state.value.copy(projects = list)
        }
    }

    suspend fun loadProjectsSuspend() {
        val list = withContext(Dispatchers.IO) {
                projectService.getAllProjects(syncCloud = true)
        }
        _state.value = _state.value.copy(projects = list)
    }

    fun restoreWorkspaceDraft() {
        viewModelScope.launch {
            val draft = WorkspaceAutoSaveManager.getAiDraft()
            if (draft != null) {
                val restored = WorkspaceAutoSaveManager.deserializeAiState(draft.stateJson)
                if (restored != null) {
                    _state.value = _state.value.copy(
                        selectedRatio = restored.selectedRatio,
                        inputText = restored.inputText,
                        contentType = restored.contentType,
                        contentTone = restored.contentTone,
                        targetPlatform = restored.targetPlatform,
                        projectTitle = restored.projectTitle,
                        currentProjectId = restored.currentProjectId,
                        videoDuration = restored.videoDuration,
                        editingStyle = restored.editingStyle,
                        voiceOver = restored.voiceOver,
                        musicVibe = restored.musicVibe,
                        styleDescription = restored.styleDescription,
                        selectedTemplate = restored.selectedTemplate,
                        videoQuality = restored.videoQuality,
                        ambientSound = restored.ambientSound,
                        finalVideoPath = restored.finalVideoPath,
                        libraryScenes = restored.scenes,
                        mediaResources = restored.mediaResources,
                        appState = if (restored.scenes.isNotEmpty()) AppState.REVIEW else restored.appState
                    )
                }
            }
        }
    }

}

class ProjectViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProjectViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QabasStudioScreen() {
    QabasApp()
}

/**
 * ق85: تصنيف رابط qabas://auth من نصه وحده (دالة خالصة قابلة
 * للاختبار بلا أندرويد). يعيد true لرابط استعادة كلمة مرور:
 * نوع صريح type=recovery في الاستعلام أو الشظية، أو رابط بلا
 * نوع مع طلب استعادة مسجل حديثاً (PasswordRecoveryManager).
 * ما عداه — ومنه type=signup الصريح — رابط تأكيد بريد.
 */
fun classifyQabasAuthLink(uriString: String, recoveryRequested: Boolean): Boolean {
    val query = uriString.substringAfter('?', "").substringBefore('#')
    val fragment = uriString.substringAfter('#', "")
    val type = sequenceOf(query, fragment).flatMap { part ->
        part.split('&').mapNotNull { kv ->
            val eq = kv.split('=', limit = 2)
            if (eq.size == 2 && eq[0] == "type") eq[1] else null
        }
    }.firstOrNull()
    return type == "recovery" || (type == null && recoveryRequested)
}
