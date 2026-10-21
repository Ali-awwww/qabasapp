package com.qabas.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
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
import com.qabas.app.ui.components.GlassmorphicSurface
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

@Composable
fun RatioButton(
    text: String,
    isSelected: Boolean,
    gradient: androidx.compose.ui.graphics.Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color.Transparent else DeepSlate
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isSelected) gradient
                    else androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    if (isSelected) Color.Transparent else Color(0xFF333333),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (isSelected) DeepSlate else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle deep link: qabas://keys/import?gemini=...&groq=...
        handleDeepLink(intent)

        ThemeManager.init(this)
        HomeScreenStyleManager.init(this)
        UserPreferencesManager.init(this)
        AppSoundManager.init(this)
        NotificationPreferencesManager.init(this)
        QabasNotificationHelper.initializeChannels(this)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                StyleBrain.init(this@MainActivity)
                // إصلاح القوة الوهمية 50 عندما السمات فارغة
                StyleBrainLoadFix.repairPersistedEmptyCore(this@MainActivity)
                StyleManager.init(this@MainActivity)
                NotificationPreferencesManager.scheduleAllConfiguredAlarms(this@MainActivity)
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

            MyApplicationTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides layoutDirection,
                    LocalContext provides localizedContext,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity
                ) {
                    LaunchedEffect(Unit) {
                        launch(Dispatchers.IO) {
                            runCatching {
                                LeagueService.checkAndProcessWeeklyLeague(this@MainActivity)
                                PointsManager.checkAndAwardDailyLogin(this@MainActivity)
                            }
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
                            }
                        }
                    }
                    AppStartupPermissionHandler()
                    
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
        val view = LocalView.current
        val scope = rememberCoroutineScope()
        
        val prefs = context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
        val currentEmail = prefs.getString("user_email", "") ?: ""
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isRegisteredUser = isLoggedIn && currentEmail.isNotBlank() && currentEmail != "guest@qabas.studio"

        var showFab by remember { mutableStateOf(prefs.getBoolean("show_floating_debug_fabs", false)) }
        var showReportDialog by remember { mutableStateOf(false) }
        var showMyReportsDialog by remember { mutableStateOf(false) }
        var showChatScreen by remember { mutableStateOf(false) }
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

        val myReports by BugReportRepository.reports.collectAsState()

        Box(modifier = Modifier.fillMaxSize().background(DeepSlate)) {
            if (!showChatScreen) {
                content()
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070B14))) {
                    DevChatScreen(userEmail = currentEmail, onBack = { showChatScreen = false })
                }
            }

            // 🛰️ زر الكاميرا الفضائي العائم لالتقاط لقطات الشاشة وإرسالها للمطور (مزروع في جميع الشاشات وقابل للتحريك لأي زاوية)
            val isDevScreen = BugReportManager.currentScreen.contains("DEVELOPER", ignoreCase = true)
            if (isRegisteredUser && !showChatScreen && !showReportDialog && !showMyReportsDialog && !isDevScreen) {
                SpatialDraggableDevCameraButton(
                    onScreenshotCaptured = { bitmap, file ->
                        screenshotBitmap = bitmap
                        screenshotFile = file
                        attachedTelemetry = null
                        showReportDialog = true
                    },
                    onOpenDevChat = {
                        showChatScreen = true
                    },
                    onOpenMyReports = {
                        showMyReportsDialog = true
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
                Dialog(
                    onDismissRequest = { if (!isSubmitting) showReportDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    GlassmorphicSurface(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(26.dp),
                        borderColor = GoldPrimary.copy(alpha = 0.5f)
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
                                    Icon(Icons.Default.BugReport, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                    Text(
                                        "إبلاغ أو اقتراح تحسين",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Surface(
                                    onClick = {
                                        AppSoundManager.playClick()
                                        showReportDialog = false
                                        showMyReportsDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = GoldPrimary.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.ListAlt, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                        Text(
                                            if (myReports.isNotEmpty()) "طلباتي (${myReports.size})" else "طلباتي السابقة",
                                            color = GoldPrimary,
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // معاينة اللقطة
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = screenshotBitmap,
                                        contentDescription = "Screenshot Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                    // شارة التشخيص الدقيق المرفق إن وُجد
                                    attachedTelemetry?.let { tel ->
                                        Surface(
                                            shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                                            color = Color.Black.copy(alpha = 0.85f),
                                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Troubleshoot, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(13.dp))
                                                Text(
                                                    text = "بصمة تشخيصية: ${tel.detectedProbableIssue.title}",
                                                    color = Color(0xFF00E5FF),
                                                    fontFamily = CairoFont,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // نوع البلاغ
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "BUG" to "🐞 خلل عام",
                                    "UI_OVERFLOW" to "📐 تداخل عناصر",
                                    "IMPROVEMENT" to "💡 تحسين",
                                    "CONTENT" to "📜 محتوى"
                                ).forEach { (type, label) ->
                                    val selected = reportType == type
                                    val chipColor = if (type == "UI_OVERFLOW") Color(0xFF00E5FF) else GoldPrimary
                                    Surface(
                                        onClick = {
                                            AppSoundManager.playToggle()
                                            reportType = type
                                            if (type == "UI_OVERFLOW" && description.isBlank()) {
                                                description = "يوجد تداخل بين عناصر الواجهة (نصوص/أزرار/شارات) في هذه الشاشة."
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) chipColor else Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, if (selected) chipColor else Color.White.copy(alpha = 0.15f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            label,
                                            color = if (selected) DeepSlate else Color.White,
                                            fontSize = 10.5.sp,
                                            fontFamily = CairoFont,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 7.dp)
                                        )
                                    }
                                }
                            }

                            // اختصارات سريعة للمشكلات الشائعة وزر التشخيص الفوري
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "تشخيص النظام 🔬" to "DIAGNOSE_NOW",
                                    "تداخل نصوص 📐" to "يوجد تداخل وازدحام بين النصوص والأزرار في هذه الشاشة.",
                                    "عناصر مقصوصة ✂️" to "توجد عناصر أو نصوص مقصوصة وغير كاملة الظهور."
                                ).forEach { (chipTitle, chipAction) ->
                                    val isDiagnosticChip = chipAction == "DIAGNOSE_NOW"
                                    Surface(
                                        onClick = {
                                            AppSoundManager.playClick()
                                            if (isDiagnosticChip) {
                                                val telemetry = PrecisionDiagnosticEngine.harvestTelemetry(
                                                    context = context,
                                                    screenName = BugReportManager.currentScreen,
                                                    widthDp = context.resources.configuration.screenWidthDp.toFloat(),
                                                    heightDp = context.resources.configuration.screenHeightDp.toFloat()
                                                )
                                                attachedTelemetry = telemetry
                                                reportType = telemetry.detectedProbableIssue.code
                                                val summary = "• رصد آلي: ${telemetry.detectedProbableIssue.title}\n• الأبعاد: ${telemetry.widthDp}×${telemetry.heightDp} dp (${telemetry.densityDpi} DPI)\n• الذاكرة: ${telemetry.usedRamMb}/${telemetry.maxRamMb} MB"
                                                description = if (description.isBlank()) summary else "$description\n$summary"
                                                Toast.makeText(context, "تم إجراء التشخيص الدقيق وإرفاق البصمة بنجاح 🔬", Toast.LENGTH_SHORT).show()
                                            } else {
                                                if (chipTitle.contains("تداخل")) {
                                                    reportType = "UI_OVERFLOW"
                                                }
                                                description = if (description.isBlank()) chipAction else "$description\n• $chipAction"
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDiagnosticChip) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF1E293B).copy(alpha = 0.6f),
                                        border = BorderStroke(0.8.dp, if (isDiagnosticChip) Color(0xFF00E5FF).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            chipTitle,
                                            color = if (isDiagnosticChip) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.85f),
                                            fontSize = 9.sp,
                                            fontFamily = CairoFont,
                                            fontWeight = if (isDiagnosticChip) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // الوصف
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { if (it.length <= 500) description = it },
                                    placeholder = { Text("صف المشكلة أو الاقتراح بالتفصيل هنا...", color = Color.Gray, fontSize = 11.5.sp) },
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = CairoFont, fontSize = 12.5.sp, color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                    )
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Text(
                                        "${description.length}/500",
                                        color = if (description.length > 450) Color(0xFFEF4444) else TextSecondary,
                                        fontSize = 9.5.sp,
                                        fontFamily = CairoFont
                                    )
                                }
                            }

                            // أزرار التحكم
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        AppSoundManager.playClick()
                                        showReportDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("إلغاء", color = Color.White, fontFamily = CairoFont)
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
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isSubmitting
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Send, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إرسال", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showMyReportsDialog) {
                UserReportsDialog(onDismissRequest = { showMyReportsDialog = false })
            }

            if (showCameraChooser) {
                Dialog(onDismissRequest = { showCameraChooser = false }) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1E293B)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("إرفاق صورة للبلاغ", color = GoldPrimary, fontFamily = CairoFont, fontSize = 15.sp)
                            TextButton(onClick = {
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
                            }) { Text("لقطة شاشة 📸", color = Color.White, fontFamily = CairoFont) }
                            TextButton(onClick = {
                                showCameraChooser = false
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    val file = File(context.cacheDir, "bug_camera_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    pendingCameraFileUri = uri
                                    cameraLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }) { Text("تصوير الآن 📷", color = Color.White, fontFamily = CairoFont) }
                            TextButton(onClick = {
                                showCameraChooser = false
                                galleryLauncher.launch("image/*")
                            }) { Text("من المعرض 🖼️", color = Color.White, fontFamily = CairoFont) }
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
        
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            if (update != null) {
                val pulse by rememberInfiniteTransition(label = "otaHud").animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
                    label = "otaPulse"
                )
                Dialog(
                    onDismissRequest = { UpdateManager.dismissUpdate() },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    UpdateHudPanel(
                        update = update,
                        downloadProgress = downloadProgress,
                        pulseAlpha = pulse,
                        onLater = { UpdateManager.dismissUpdate() },
                        onInstall = { UpdateDownloadService.start(context, update) }
                    )
                }
            }
        }
    }

    @Composable
    private fun UpdateHudPanel(
        update: UpdateManager.UpdateInfo,
        downloadProgress: UpdateManager.DownloadProgress?,
        pulseAlpha: Float,
        onLater: () -> Unit,
        onInstall: () -> Unit
    ) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .wrapContentHeight()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            shape = RoundedCornerShape(28.dp),
            backgroundColor = CardSurface.copy(alpha = 0.82f),
            borderColor = GoldPrimary.copy(alpha = 0.55f)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "// OTA UPDATE CHANNEL",
                        color = GoldPrimary.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(GoldPrimary.copy(alpha = 0.13f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    "تحديث جديد متاح",
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    "إصدار وقت التشغيل التالي جاهز على قناة التحديث",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UpdateVersionChip(
                        label = "الإصدار الحالي",
                        value = "v${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
                        gold = false,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    UpdateVersionChip(
                        label = "الإصدار الجديد",
                        value = "v${update.versionName} · ${update.versionCode}",
                        gold = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (update.releaseNotes.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 130.dp)
                            .verticalScroll(rememberScrollState())
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardSurface.copy(alpha = 0.45f))
                            .border(1.dp, TextSecondary.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "·",
                                color = GoldPrimary,
                                fontSize = 14.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "ما الجديد:",
                                color = GoldSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFont
                            )
                        }
                        Text(
                            update.releaseNotes,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = CairoFont
                        )
                    }
                }

                if (downloadProgress != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (downloadProgress.phase) {
                                    UpdateManager.Phase.DOWNLOADING_DELTA -> "جلب الدلتا الصغيرة..."
                                    UpdateManager.Phase.APPLYING_PATCH -> "تطبيق التحديث التفاضلي..."
                                    UpdateManager.Phase.DOWNLOADING_FULL -> "تنزيل الملف الكامل..."
                                },
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                fontFamily = CairoFont,
                                maxLines = 1
                            )
                            Text(
                                "${downloadProgress.percent}%",
                                color = GoldPrimary,
                                fontSize = 11.sp,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(TextSecondary.copy(alpha = 0.15f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((downloadProgress.percent / 100f).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(GoldPrimary, GoldSecondary)))
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable(onClick = onLater),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "لاحقاً",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.4f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.horizontalGradient(listOf(GoldPrimary, GoldSecondary.copy(alpha = 0.9f))))
                                .clickable(onClick = onInstall),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = DeepSlate,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "تحديث الآن",
                                    color = DeepSlate,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Text(
                    if (update.deltaUrl != null)
                        "Δ دلتا ${UpdateManager.formatSize(update.deltaSize)}  ·  كامل ${UpdateManager.formatSize(update.apkSize)}"
                    else
                        "حجم التحميل ${UpdateManager.formatSize(update.apkSize)}",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = CairoFont,
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun UpdateVersionChip(
        label: String,
        value: String,
        gold: Boolean,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (gold) GoldPrimary.copy(alpha = 0.10f) else CardSurface.copy(alpha = 0.5f))
                .border(
                    1.dp,
                    if (gold) GoldPrimary.copy(alpha = 0.45f) else TextSecondary.copy(alpha = 0.25f),
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    label,
                    color = TextSecondary,
                    fontSize = 9.5.sp,
                    fontFamily = CairoFont,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    value,
                    color = if (gold) GoldPrimary else TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
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
    }

    override fun onResume() {
        super.onResume()
        UpdateManager.onAppForeground()
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
            "quran_hub" -> AppState.QURAN_HUB
            "hadith_studio" -> AppState.HADITH_STUDIO
            "azkar" -> AppState.AZKAR
            "rewards" -> AppState.REWARDS
            "downloads", "download_manager" -> AppState.DOWNLOAD_MANAGER
            "settings" -> AppState.SETTINGS
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
        if (uri != null && uri.host == QabasWebConfig.OFFICIAL_WEB_HOST) {
            val path = uri.path.orEmpty()
            android.widget.Toast.makeText(this, "مرحباً بك في تطبيق قبس عبر الموقع الرسمي 🌐", android.widget.Toast.LENGTH_SHORT).show()
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
                android.widget.Toast.makeText(this, "جاري التحقق من جميع المفاتيح...", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun QabasBottomNavigation(
    currentRoute: AppState,
    onNavigate: (AppState) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = remember {
        listOf(
            Triple(AppState.HOME, Icons.Default.Home, "الرئيسية"),
            Triple(AppState.AI_STUDIO_AGENT, Icons.Default.SmartToy, "الوكيل"),
            Triple(AppState.REELS, Icons.Default.SlowMotionVideo, "ريلز"),
            Triple(AppState.STUDIO_GATEWAY, Icons.Default.AutoAwesome, "الاستوديو"),
            Triple(AppState.HASANAT_HUB, Icons.Default.Mosque, "المِشكاة"),
            Triple(AppState.SETTINGS, Icons.Default.SettingsSuggest, "الإعدادات")
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ambientColor = GoldPrimary.copy(alpha = 0.15f),
                spotColor = GoldSecondary.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.08f),
                        GoldPrimary.copy(alpha = 0.5f),
                        GoldSecondary.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ),
        color = CardSurface.copy(alpha = 0.95f)
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = GoldPrimary,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
        ) {
            navItems.forEach { (route, icon, labelKey) ->
                val label = Translator.tr(labelKey)
                val isSelected = currentRoute == route
                val iconScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "nav_icon_scale"
                )

                NavigationBarItem(
                    modifier = Modifier.testTag(TestTags.NAV_ITEM_PREFIX + route.name),
                    icon = {
                        Icon(
                            imageVector = icon,
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    },
                    selected = isSelected,
                    onClick = {
                        AppSoundManager.playTab()
                        onNavigate(route)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (ThemeManager.activePreset.isDark) DeepSlate else Color.White,
                        unselectedIconColor = TextSecondary,
                        selectedTextColor = GoldPrimary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = GoldPrimary
                    )
                )
            }
        }
    }
}

enum class AppState {
    PREMIUM_UPGRADE, DATA_LOADING, ONBOARDING, LOGIN, REGISTER, FORGOT_PASSWORD,
    DEVELOPER_DASHBOARD, DEVELOPER_KEY_STATUS_HUB, HOME, PROJECTS, INPUT, UNDERSTANDING, RESOURCES, PROCESSING, REVIEW,
    ADVANCED_EDIT, PAYMENT, SAVE_SHARE, SAVE_PROJECT, API_DOCS, SETTINGS, TELEPROMPTER,
    AUDIO_LIBRARY, AI_ASSISTANT, YOUTUBE_STUDIO, LEADERBOARD, PROFILE, APP_IDEA_FORM,
    REQUEST_CHAT, NOTIFICATIONS, REQUEST_DETAILS, TASTE_PROFILE, SMART_DIRECTOR, REWARDS,
    KNOWLEDGE_HUB, CONTENT_GUARD, HASANAT_HUB, QURAN_HUB, HADITH_STUDIO, ENTERPRISE_PORTAL, VIDEO_STYLE_CLONER,
    PHOTO_STUDIO, STYLE_SELECTION, CREATE_STYLE_OBJECT, DIRECTOR_CHAT,
    QASAS, TRANSCRIPT_EDIT, CLIP_EXTRACTOR, CAMERA_CAPTURE, SCHOLAR_BIOGRAPHIES, SCHOLAR_DETAIL,
    AZKAR, ISLAMIC_LIBRARY, PRAYER_TIMES, STUDIO_GATEWAY, PRO_EDITOR, SOCIAL_STATS,
    ABOUT_US, MONEY_PRINTER_TURBO, VOICE_STUDIO, UNIFIED_MASTER_STUDIO, MEDIA_BROWSER,
    PERMISSIONS_HUB, CRASH_LOGS_HUB, REELS, RULES, AI_STUDIO_AGENT, AGENT_TEAM_CONFIG, SAVED_ITEMS,
    DOWNLOAD_MANAGER
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
    val showChatSheet: Boolean = false,
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
    val selectedVerseNumber: Int? = null
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
                if (previousStatus != null && previousStatus != status) {
                    if (status == ConnectivityObserver.Status.Available) {
                        Toast.makeText(context, "تم استعادة الاتصال السحابي 🚀", Toast.LENGTH_SHORT).show()
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
                    lastScreen = s.appState.name
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
                appState = AppState.INPUT
            )
        }
    }

    fun openProject(project: ProjectService.Project) {
        updateState {
            copy(
                currentProjectId = project.id,
                projectTitle = project.title,
                inputText = project.idea,
                appState = try {
                    AppState.valueOf(project.lastScreen)
                } catch (_: Exception) {
                    AppState.INPUT
                }
            )
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
                projectService.getAllProjects()
            }
            _state.value = _state.value.copy(projects = list)
        }
    }

    suspend fun loadProjectsSuspend() {
        val list = withContext(Dispatchers.IO) {
            projectService.getAllProjects()
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

    fun dismissWorkspaceDraft() {
        viewModelScope.launch {
            WorkspaceAutoSaveManager.clearAiDraft()
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

@Composable
fun ChoiceChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) GoldPrimary else CardSurface)
            .border(
                1.dp,
                if (isSelected) GoldPrimary else Color(0xFF1E293B),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) DeepSlate else TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun StyleCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) CardSurface.copy(alpha = 0.8f) else CardSurface)
            .border(
                1.dp,
                if (isSelected) GoldPrimary else Color(0xFF1E293B),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                color = if (isSelected) GoldPrimary else TextPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = description,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
fun PlayerBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = GoldPrimary,
            fontSize = 12.sp,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold
        )
    }
}

fun buildCinematicPrompt(
    userIdea: String,
    duration: String,
    style: String,
    aspectRatio: String,
    voiceOver: String,
    musicVibe: String
): String {
    val styleInstructions = when {
        style.contains("3nvus") || style.contains(Translator.tr("نيون")) ->
            "Visual Style: Dark moody aesthetic, high contrast. Typography: Kinetic glowing neon text synced to audio. Pacing: Fast viral transitions with ambient sound design."
        style.contains(Translator.tr("سينمائي")) ->
            "Visual Style: Deep warm tones, elegant cinematic lighting, slow-motion B-roll, premium and smooth transitions."
        style.contains(Translator.tr("وثائقي")) ->
            "Visual Style: Fast-paced documentary style, modern grid overlays, dynamic zooms, informative tone."
        else -> ""
    }

    val voiceInstruction = when {
        voiceOver.contains(Translator.tr("عميق")) -> "Voice Over Tone: Deep, Epic, authoritative."
        voiceOver.contains(Translator.tr("حماسي")) -> "Voice Over Tone: Energetic, fast-paced, motivating."
        voiceOver.contains(Translator.tr("رسمي")) -> "Voice Over Tone: Neutral, official, clear and documentary-style."
        else -> "Voice Over Tone: $voiceOver"
    }

    val soundEffectInstruction = when {
        musicVibe.contains(Translator.tr("طبيعة")) ->
            "Sound Effect Vibe: Nature sounds, birds, wind, flowing water."
        musicVibe.contains(Translator.tr("مدينة")) ->
            "Sound Effect Vibe: Urban ambience, traffic, distant chatter."
        musicVibe.contains(Translator.tr("هادئ")) ->
            "Sound Effect Vibe: Calm, minimal, subtle ambient white noise."
        musicVibe.contains(Translator.tr("نشاط")) ->
            "Sound Effect Vibe: Active, gym or sports ambient, energetic non-musical sounds."
        musicVibe.contains(Translator.tr("درامي")) ->
            "Sound Effect Vibe: Dramatic swooshes, impacts, cinematic non-musical sound effects."
        else -> "Sound Effect Vibe: $musicVibe"
    }

    return """
        [MASTER PROMPT]
        Duration: $duration
        Aspect Ratio: $aspectRatio
        
        $styleInstructions
        $voiceInstruction
        $soundEffectInstruction
        
        User Concept/Script:
        $userIdea
    """.trimIndent()
}
