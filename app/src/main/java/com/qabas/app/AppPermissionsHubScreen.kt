package com.qabas.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 🛸 مركز التحكم السيبراني للأذونات وحماية النظام
 * Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 *
 * يوفر لوحة قيادة فضائية متطورة لفحص الأذونات، اختبار العتاد (ميكروفون، كاميرا، إشعارات)،
 * وضمان عمل الأذان والتنبيهات بالخلفية بدون قيود مع أعلى معايير الشفافية والخصوصية.
 */

data class HudPermissionItem(
    val id: String,
    val title: String,
    val technicalCode: String,
    val subsystemCategory: String,
    val description: String,
    val impactWhenDisabled: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isCritical: Boolean,
    val accentColor: Color,
    val onAction: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionsHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live permission states
    var isBatteryIgnored by remember { mutableStateOf(BackgroundPermissionManager.isBatteryOptimizationIgnored(context)) }
    var canExactAlarm by remember { mutableStateOf(BackgroundPermissionManager.canScheduleExactAlarms(context)) }
    var canFullScreen by remember { mutableStateOf(BackgroundPermissionManager.canUseFullScreenIntent(context)) }
    var isMicGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var isCameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var isNotificationGranted by remember {
        mutableStateOf(BackgroundPermissionManager.areNotificationsEnabled(context))
    }

    var diagnosticMessage by remember { mutableStateOf<String?>(null) }
    var testWaveformLevel by remember { mutableFloatStateOf(0f) }
    var isTestingMic by remember { mutableStateOf(false) }

    fun refreshAllPermissionStates() {
        isBatteryIgnored = BackgroundPermissionManager.isBatteryOptimizationIgnored(context)
        canExactAlarm = BackgroundPermissionManager.canScheduleExactAlarms(context)
        isNotificationGranted = BackgroundPermissionManager.areNotificationsEnabled(context)
        canFullScreen = BackgroundPermissionManager.canUseFullScreenIntent(context)
        isMicGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        isCameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    // Auto-refresh when returning from Android System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllPermissionStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshAllPermissionStates()
        BackgroundPermissionManager.refreshBackgroundSchedulers(context)
    }

    // Handle single permission launchers
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshAllPermissionStates()
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshAllPermissionStates()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshAllPermissionStates()
    }

    // Calculate system readiness score
    val totalCount = 6
    val grantedCount = listOf(isBatteryIgnored, canExactAlarm, isNotificationGranted, isMicGranted, isCameraGranted, canFullScreen).count { it }
    val readinessPercentage = (grantedCount * 100) / totalCount

    // Pulsing cybernetic animation for the radar HUD
    val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rot"
    )

    // Real hardware microphone capture and RMS signal level testing (No fake simulation)
    LaunchedEffect(isTestingMic) {
        if (isTestingMic) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPerm) {
                isTestingMic = false
                testWaveformLevel = 0f
                Toast.makeText(context, "إذن الميكروفون غير ممنوح، يرجى تفعيله أولاً ⚠️", Toast.LENGTH_SHORT).show()
                return@LaunchedEffect
            }

            var maxAmplitude = 0f
            var sampleSuccess = false

            withContext(Dispatchers.IO) {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                if (minBufSize > 0) {
                    var audioRecord: AudioRecord? = null
                    try {
                        audioRecord = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            minBufSize * 2
                        )

                        if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecord.startRecording()
                            val buffer = ShortArray(minBufSize)

                            // Sample real hardware buffers for 20 frames (~2.4s)
                            for (step in 1..20) {
                                val readCount = audioRecord.read(buffer, 0, buffer.size)
                                if (readCount > 0) {
                                    var sumSquare = 0.0
                                    for (i in 0 until readCount) {
                                        val sample = buffer[i].toDouble()
                                        sumSquare += sample * sample
                                    }
                                    val rms = kotlin.math.sqrt(sumSquare / readCount).toFloat()
                                    val normalizedLevel = (rms / 6000f).coerceIn(0f, 1.0f)
                                    if (normalizedLevel > maxAmplitude) maxAmplitude = normalizedLevel
                                    withContext(Dispatchers.Main) {
                                        testWaveformLevel = normalizedLevel
                                    }
                                    sampleSuccess = true
                                }
                                delay(120)
                            }
                            audioRecord.stop()
                        }
                    } catch (e: SecurityException) {
                        sampleSuccess = false
                    } catch (e: Exception) {
                        sampleSuccess = false
                    } finally {
                        try {
                            audioRecord?.release()
                        } catch (_: Exception) {}
                    }
                }
            }

            isTestingMic = false
            testWaveformLevel = 0f

            // التحقق من وجود إشارة حقيقية لضمان نجاح الاختبار فعلياً (منع النجاح العشوائي/الصامت)
            val minThreshold = 0.15f
            if (sampleSuccess && maxAmplitude >= minThreshold) {
                val percent = (maxAmplitude * 100).toInt()
                Toast.makeText(
                    context,
                    "تم التحقق من استجابة الميكروفون الفعلي بنجاح 🎙️ (مستوى الإشارة: $percent%)",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (sampleSuccess) {
                Toast.makeText(
                    context,
                    "تم رصد الميكروفون ولكن الإشارة ضعيفة جداً أو صامتة 🔇. يرجى التحدث بوضوح.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, "تعذر تشغيل الميكروفون أو العتاد مستخدم من تطبيق آخر ⚠️", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler { onBack() }
    val theme = QabasTheme.current

    Scaffold(
        topBar = {
            AppleTopBar(
                title = "أذونات وحماية النظام",
                subtitle = "إدارة أذونات العتاد والتشغيل بالخلفية",
                onBack = onBack,
                backTestTag = "permissions_hub_back_btn",
                actions = {
                    IconButton(
                        onClick = {
                            refreshAllPermissionStates()
                            Toast.makeText(context, "تم تحديث حالة الأذونات ⚡", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "تحديث",
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        },
        containerColor = theme.background
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(theme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── 1. بطاقة القياس الفضائي الرئيسية (Spatial Telemetry & Readiness Gauge) ──
                HudReadinessTelemetryCard(
                    readinessPercentage = readinessPercentage,
                    grantedCount = grantedCount,
                    totalCount = totalCount,
                    pulseAlpha = pulseAlpha,
                    radarRotation = radarRotation,
                    onDeployAll = {
                        val missingRuntime = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationGranted) {
                            missingRuntime.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (!isMicGranted) missingRuntime.add(Manifest.permission.RECORD_AUDIO)
                        if (!isCameraGranted) missingRuntime.add(Manifest.permission.CAMERA)

                        if (missingRuntime.isNotEmpty()) {
                            multiplePermissionLauncher.launch(missingRuntime.toTypedArray())
                        }

                        if (!isBatteryIgnored) {
                            BackgroundPermissionManager.requestIgnoreBatteryOptimizations(context)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExactAlarm) {
                            BackgroundPermissionManager.requestScheduleExactAlarm(context)
                        }

                        BackgroundPermissionManager.refreshBackgroundSchedulers(context)
                        refreshAllPermissionStates()
                        AppSoundManager.playClick()
                    },
                    onRunDiagnostics = {
                        refreshAllPermissionStates()
                        BackgroundPermissionManager.refreshBackgroundSchedulers(context)

                        val currentPermissions = listOf(
                            "تجاهل تحسين البطارية" to BackgroundPermissionManager.isBatteryOptimizationIgnored(context),
                            "المنبهات الدقيقة" to BackgroundPermissionManager.canScheduleExactAlarms(context),
                            "الإشعارات" to BackgroundPermissionManager.areNotificationsEnabled(context),
                            "العرض بملء الشاشة" to BackgroundPermissionManager.canUseFullScreenIntent(context),
                            "الميكروفون" to (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED),
                            "الكاميرا" to (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                        )
                        val enabledCount = currentPermissions.count { it.second }
                        val missingPermissions = currentPermissions.filterNot { it.second }.joinToString("، ") { it.first }
                        val resultMessage = if (missingPermissions.isEmpty()) {
                            "تم تحديث الفحص: جميع الأذونات الستة المطلوبة مفعّلة."
                        } else {
                            "نتيجة الفحص: $enabledCount من ${currentPermissions.size} أذونات مفعّلة. غير مفعّل: $missingPermissions."
                        }
                        diagnosticMessage = resultMessage
                        Toast.makeText(context, resultMessage, Toast.LENGTH_LONG).show()
                    }
                )

                diagnosticMessage?.let { result ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0C1322),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = result,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // ── 2. مصفوفة الصلاحيات التفصيلية (Cybernetic Matrix Cards) ──
                Text(
                    text = "مصفوفة العتاد والصلاحيات الحيوية ✦",
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )

                // 1. ميكروفون استوديو الصوتيات
                CyberPermissionCard(
                    title = "تسجيل الصوتيات والتلاوة",
                    code = "RECORD_AUDIO • 48kHz HD",
                    subsystem = "استوديو التلاوة والبودكاست",
                    desc = "يتيح تسجيل التلاوات العذبة، التعليق الصوتي، والمقاطع الحوارية في استوديو خادم المسلم.",
                    impact = "بدونه لا يمكن تسجيل الصوت مباشرة داخل التطبيق.",
                    icon = Icons.Default.Mic,
                    isGranted = isMicGranted,
                    accentColor = Color(0xFF38BDF8),
                    testWaveformLevel = if (isTestingMic) testWaveformLevel else 0f,
                    actionLabel = if (isMicGranted) "اختبار الميكروفون 🎙️" else "منح الإذن ⚡",
                    onAction = {
                        if (isMicGranted) {
                            isTestingMic = true
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )

                // 2. الكاميرا واستوديو الفيديو
                CyberPermissionCard(
                    title = "الكاميرا والتصوير السينمائي",
                    code = "CAMERA • 1080p/4K HDR",
                    subsystem = "استوديو الفيديو وصناعة المحتوى",
                    desc = "يتيح تصوير المشاهد الحية والقصص وإضافتها مباشرة لمحرر الفيديو والذكاء الاصطناعي.",
                    impact = "بدونه يقتصر استيراد المشاهد على مكتبة الوسائط فقط.",
                    icon = Icons.Default.CameraAlt,
                    isGranted = isCameraGranted,
                    accentColor = Color(0xFFA855F7),
                    actionLabel = if (isCameraGranted) "مفعل وجاهز ✓" else "منح الإذن ⚡",
                    onAction = {
                        if (!isCameraGranted) {
                            cameraLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            Toast.makeText(context, "الكاميرا جاهزة للعمل في استوديو الفيديو 📷", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // 3. الإشعارات الذكية والمنارة
                CyberPermissionCard(
                    title = "الإشعارات والتنبيهات الروحانية",
                    code = "POST_NOTIFICATIONS • PUSH BEACON",
                    subsystem = "منظومة التذكير بالأذكار والصلوات",
                    desc = "إرسال تنبيهات الأذكار الصباحية والمسائية، مواقيت الصلاة، وإشعارات انتهاء رندرة الفيديو.",
                    impact = "بدونه لن تظهر تنبيهات الأذكار والتحديثات على شاشة القفل.",
                    icon = Icons.Default.NotificationsActive,
                    isGranted = isNotificationGranted,
                    accentColor = Color(0xFFF59E0B),
                    actionLabel = if (isNotificationGranted) "إرسال تجربة 🔔" else "تفعيل التنبيهات ⚡",
                    onAction = {
                        if (isNotificationGranted) {
                            NotificationHelper.showNotification(
                                context,
                                "تجربة إشعارات استوديو خادم المسلم 🕋",
                                "نظام الإشعارات والتنبيهات الروحانية يعمل بأعلى درجات الدقة والجاهزية!"
                            )
                            Toast.makeText(context, "تم إرسال إشعار تجريبي لشريط الحالة 🔔", Toast.LENGTH_SHORT).show()
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                BackgroundPermissionManager.openNotificationSettings(context)
                            }
                        }
                    }
                )

                // 4. المنبهات الدقيقة والأذان في وقته
                CyberPermissionCard(
                    title = "المنبهات الدقيقة لمواقيت الصلاة",
                    code = "SCHEDULE_EXACT_ALARM • TEMPORAL SENTINEL",
                    subsystem = "محرك رفع الأذان الفوري",
                    desc = "جدولة تنبيهات الأذان بالثانية المحددة عند دخول الوقت دون أي تأخير من النظام.",
                    impact = "بدونه قد يتأخر رفع الأذان لعدة دقائق بسبب جدولة أندرويد التقريبية.",
                    icon = Icons.Default.AlarmOn,
                    isGranted = canExactAlarm,
                    accentColor = Color(0xFF10B981),
                    actionLabel = if (canExactAlarm) "دقيق بالثانية ✓" else "ضبط المنبهات الدقيقة ⚡",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            BackgroundPermissionManager.requestScheduleExactAlarm(context)
                        } else {
                            Toast.makeText(context, "المنبهات الدقيقة مدعومة افتراضياً في إصدار جهازك ✅", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // 4.5. إذن الأذان بملء الشاشة (Android 14+)
                if (Build.VERSION.SDK_INT >= 34) {
                    CyberPermissionCard(
                        title = "الأذان بملء الشاشة",
                        code = "USE_FULL_SCREEN_INTENT • HUD_OVERLAY",
                        subsystem = "محرك واجهة الأذان فوق القفل",
                        desc = "السماح للأذان بفتح واجهة التنبيه مباشرة فوق شاشة القفل عند دخول الوقت لضمان التنبيه الفوري.",
                        impact = "بدونه قد يكتفي النظام بإرسال إشعار صامت أو بسيط دون فتح الواجهة.",
                        icon = Icons.Default.Screenshot,
                        isGranted = canFullScreen,
                        accentColor = Color(0xFFF43F5E),
                        actionLabel = if (canFullScreen) "مفعل وجاهز ✓" else "منح الإذن ⚡",
                        onAction = {
                            BackgroundPermissionManager.requestFullScreenIntentPermission(context)
                        }
                    )
                }

                // 5. استثناء توفير البطارية (خلفية دون قيود)
                CyberPermissionCard(
                    title = "درع حماية العمل بالخلفية (استثناء البطارية)",
                    code = "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS • ZERO-THROTTLE",
                    subsystem = "حارس البقاء النشط للأذان والرندرة",
                    desc = "منع نظام Doze Mode في أندرويد من تجميد التطبيق أثناء إغلاق الشاشة أو خمول الجهاز لضمان انطلاق الأذان.",
                    impact = "قد يمنع النظام صوت الأذان أثناء نوم الهاتف الممتد إذا لم يتم الاستثناء.",
                    icon = Icons.Default.BatteryChargingFull,
                    isGranted = isBatteryIgnored,
                    accentColor = Color(0xFF00E5FF),
                    actionLabel = if (isBatteryIgnored) "محمي من الخمول ✓" else "استثناء البطارية ⚡",
                    onAction = {
                        BackgroundPermissionManager.requestIgnoreBatteryOptimizations(context)
                    }
                )

                // 6. التشغيل التلقائي لمصنعي الأجهزة (Xiaomi / Samsung / Huawei / Oppo)
                Surface(
                    color = Color(0xFF0C1322).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                BackgroundPermissionManager.openManufacturerAutostartSettings(context)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFA855F7).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "إعدادات التشغيل التلقائي للشركة المصنعة",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "لهواتف شاومي، سامسونج، هواوي، أوبو وفيفو لمنع إغلاق الأذان بالقوة",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(18.dp))
                    }
                }

                // ── 3. ميثاق الشفافية والخصوصية الصادق (Developer Honest Privacy Protocol) ──
                Surface(
                    color = Color(0xFF070D18).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "ميثاق الأمان والخصوصية الصادق 100%",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "• الميكروفون والكاميرا لا يعملان في الخلفية مطلقاً، ولا يتم تفعيلهما إلا بضغطك المباشر داخل استوديو الإنتاج.\n" +
                                    "• كافة التلاوات والملفات الصوتية تُعالج محلياً بأعلى درجات التشفير والخصوصية.\n" +
                                    "• لا يتم جمع أي بيانات حساسة أو بيعها لأي طرف ثالث، وتطبيق خادم المسلم خالي 100% من الإعلانات التتبعية.",
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { BackgroundPermissionManager.openAppSettings(context) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إدارة أذونات النظام العامة ⚙️", color = Color(0xFF00E5FF), fontFamily = CairoFont, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 🛰️ بطاقة القياس الفضائية العلوية المحدثة بنظام HUD
 */
@Composable
private fun HudReadinessTelemetryCard(
    readinessPercentage: Int,
    grantedCount: Int,
    totalCount: Int,
    pulseAlpha: Float,
    radarRotation: Float,
    onDeployAll: () -> Unit,
    onRunDiagnostics: () -> Unit
) {
    val theme = QabasTheme.current
    val statusColor = when {
        readinessPercentage >= 100 -> theme.success
        readinessPercentage >= 60 -> theme.primary
        else -> theme.warning
    }

    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        containerColor = theme.cardSurface
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (readinessPercentage >= 100) "درع النظام: نشط ومكتمل 100%" else "حالة الأذونات: $grantedCount من $totalCount مفعلة",
                            color = statusColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "مستوى الجاهزية التشغيلية",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "لضمان رفع الأذان، تشغيل الكاميرا، وميكروفون الاستوديو بأعلى دقة",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }

                // Apple Circular Progress Metric
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(1.5.dp, statusColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$readinessPercentage%",
                            color = statusColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "جاهزية",
                            color = theme.textSecondary,
                            fontFamily = CairoFont,
                            fontSize = 8.5.sp
                        )
                    }
                }
            }

            // Quick Deploy & Diagnostic Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDeployAll,
                    modifier = Modifier
                        .weight(1.3f)
                        .defaultMinSize(minHeight = 44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (readinessPercentage >= 100) theme.success else theme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        if (readinessPercentage >= 100) Icons.Default.CheckCircle else Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (readinessPercentage >= 100) "كافة الأذونات مفعلة ✓" else "تفعيل جميع الصلاحيات 🚀",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onRunDiagnostics,
                    modifier = Modifier
                        .weight(0.9f)
                        .defaultMinSize(minHeight = 44.dp),
                    border = BorderStroke(1.dp, theme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Troubleshoot, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "فحص ذاتي ⚡",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }
}

/**
 * 🎛️ بطاقة الإذن السيبرانية المنفردة (Cybernetic Matrix Card)
 */
@Composable
private fun CyberPermissionCard(
    title: String,
    code: String,
    subsystem: String,
    desc: String,
    impact: String,
    icon: ImageVector,
    isGranted: Boolean,
    accentColor: Color,
    actionLabel: String,
    testWaveformLevel: Float = 0f,
    onAction: () -> Unit
) {
    val theme = QabasTheme.current
    val statusColor = if (isGranted) Color(0xFF10B981) else accentColor

    Surface(
        color = theme.cardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isGranted) Color(0xFF10B981).copy(alpha = 0.35f) else theme.cardBorder
        ),
        shadowElevation = if (theme.isDark) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (isGranted) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        "ممنوح ✓",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            code,
                            color = theme.textTertiary,
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Action trigger button (Apple HIG pill style)
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGranted) theme.secondarySurface else accentColor,
                        contentColor = if (isGranted) Color(0xFF10B981) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }

            // Description and Impact
            Text(
                text = desc,
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            if (!isGranted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.08f))
                        .border(BorderStroke(0.5.dp, Color(0xFFEF4444).copy(alpha = 0.2f)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = impact,
                        color = Color(0xFFEF4444),
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }
            }

            // Waveform simulation bar if mic test is active
            if (testWaveformLevel > 0f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.secondarySurface)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (i in 0..18) {
                        val measuredSegments = (testWaveformLevel * 19).toInt().coerceIn(0, 19)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(0.8f)
                                .background(
                                    color = if (i < measuredSegments) theme.primary else theme.cardBorder,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

