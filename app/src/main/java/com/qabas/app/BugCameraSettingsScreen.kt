package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

/**
 * 📸 BugCameraSettingsScreen
 * قسم التحكم الكامل في زر الكاميرا الفضائي للبلاغات والمشاكل.
 * مخصص للحسابات المسجلة لمراقبة الأخطاء، التقاط الشاشات، وفحص مقاييس الـ DP.
 * مصمم بنمط: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugCameraSettingsScreen(
    onBack: () -> Unit,
    onOpenMyReports: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SpatialCameraPreferencesManager.init(context)
    }

    // حالات التفضيلات التفاعلية
    val cameraEnabled by SpatialCameraPreferencesManager.cameraEnabled.collectAsState()
    val compactMode by SpatialCameraPreferencesManager.compactMode.collectAsState()
    val dockedMode by SpatialCameraPreferencesManager.dockedMode.collectAsState()
    val tapInstantSnap by SpatialCameraPreferencesManager.tapInstantSnap.collectAsState()
    val skipWhiteFlash by SpatialCameraPreferencesManager.skipWhiteFlash.collectAsState()
    val idleAlpha by SpatialCameraPreferencesManager.idleAlpha.collectAsState()
    val hapticEnabled by SpatialCameraPreferencesManager.hapticEnabled.collectAsState()

    // عداد البلاغات المخزنة
    val myReports by BugReportRepository.reports.collectAsState()

    // حركة أنميشن المعاينة الحية
    val infiniteTransition = rememberInfiniteTransition(label = "PreviewTransition")
    val previewRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRotation"
    )
    val previewPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarPulse"
    )

    var previewTapped by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "زر الكاميرا الفضائي للبلاغات",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "أداة التقاط الشاشة وفحص مقاييس الأخطاء",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("bug_cam_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = GoldPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SpatialCameraPreferencesManager.resetToDefaults(context)
                            Toast.makeText(context, "تمت إعادة تعيين إعدادات الكاميرا للوضع الافتراضي ✓", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("bug_cam_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "إعادة ضبط",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        },
        containerColor = qabasBackground()
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── 1. بطاقة المفتاح الرئيسي والحالة الحية (Hero Master Switch Card) ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .luxuryCardStyle(shapeRadius = 20.dp, borderAlpha = if (cameraEnabled) 0.55f else 0.25f, glowElevation = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (cameraEnabled) Color(0xFF0D1726) else Color(0xFF0A0F1A))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(if (cameraEnabled) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF334155).copy(alpha = 0.3f))
                                    .border(1.2.dp, if (cameraEnabled) Color(0xFF10B981) else Color(0xFF475569), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (cameraEnabled) Icons.Default.CameraAlt else Icons.Default.NoPhotography,
                                    contentDescription = null,
                                    tint = if (cameraEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "تفعيل زر الكاميرا الفضائي",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = if (cameraEnabled) "نشط وعائم في كافة شاشات التطبيق 🟢" else "معطل ومخفي تماماً ⚪",
                                    color = if (cameraEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Switch(
                            checked = cameraEnabled,
                            onCheckedChange = {
                                SpatialCameraPreferencesManager.setCameraEnabled(context, it)
                                AppSoundManager.playClick()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.45f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.testTag("bug_cam_master_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "ميزة حصرية للحسابات المسجلة: تمكّنك من التقاط لقطات الشاشة بنقاء، وتحديد مواضع الخلل بقلم التأشير، وقياس أبعاد الـ DP، وإرسال البلاغات التقنية مباشرة للمطور.",
                        color = Color(0xFFCBD5E1),
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // ── 2. نافذة المعاينة الحية التفاعلية (Live Interactive HUD Preview) ──
            AnimatedVisibility(visible = cameraEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.4f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF090E1A))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "المعاينة الحية التفاعلية للمظهر:",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                            Surface(
                                color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = if (compactMode) "الوضع المصغر (46dp)" else "الوضع الكامل (54dp)",
                                    color = Color(0xFF00E5FF),
                                    fontFamily = NotoSansFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // شاشة محاكاة بنمط HUD زجاجي
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF080D18), Color(0xFF0F1826))
                                    )
                                )
                                .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // خطوط شبكة HUD الدقيقة
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("HUD :: SPATIAL_LENS_v2", color = Color(0xFF334155), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                                    Text("DP_ACCURACY: 100%", color = Color(0xFF334155), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("OPACITY: ${(idleAlpha * 100).toInt()}%", color = Color(0xFF334155), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                                    Text("MODE: ${if (dockedMode) "DOCKED" else "FLOATING"}", color = Color(0xFF334155), fontFamily = RobotoMonoFont, fontSize = 9.sp)
                                }
                            }

                            // محاكاة زر الكاميرا الحي
                            val previewBtnSize = if (compactMode) 46.dp else 54.dp
                            Box(
                                modifier = Modifier
                                    .size(previewBtnSize)
                                    .alpha(idleAlpha)
                                    .scale(previewPulse)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                        )
                                    )
                                    .border(
                                        2.dp,
                                        Brush.sweepGradient(
                                            listOf(GoldPrimary, Color(0xFF00E5FF), GoldSecondary, GoldPrimary)
                                        ),
                                        CircleShape
                                    )
                                    .clickable {
                                        previewTapped = !previewTapped
                                        AppSoundManager.playClick()
                                        Toast.makeText(context, "نقرة تجريبية على الكاميرا 📸", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "معاينة الزر",
                                    tint = if (previewTapped) Color(0xFF00E5FF) else GoldPrimary,
                                    modifier = Modifier
                                        .size(if (compactMode) 22.dp else 26.dp)
                                        .rotate(if (previewTapped) 15f else 0f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "انقر على الزر أعلاه لتجربة الاستجابة البصرية والصوتية.",
                            color = Color(0xFF64748B),
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── 3. قسم خيارات ومكملات التحكم (Detailed Controls) ──
            AnimatedVisibility(visible = cameraEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "خيارات وتخصيصات الكاميرا الفضائية ⚙️",
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )

                    // 1. نمط الحجم (Compact Mode)
                    SettingSwitchCard(
                        title = "الوضع المصغر (Compact Mode)",
                        subtitle = "تقليص قطر الزر إلى 46dp ليكون خفيفاً وأنيقاً ولا يحجب المحتوى",
                        icon = Icons.Default.AspectRatio,
                        isChecked = compactMode,
                        onCheckedChange = {
                            SpatialCameraPreferencesManager.setCompactMode(context, it)
                            AppSoundManager.playClick()
                        },
                        testTag = "bug_cam_compact_switch"
                    )

                    // 2. الالتصاق بالحواف (Auto-Docking Mode)
                    SettingSwitchCard(
                        title = "الالتصاق التلقائي بالحافة (Auto Docking)",
                        subtitle = "عند تحرير الزر، ينجذب تلقائياً للحافة الجانبية كشريط لمسي نحيف 22dp",
                        icon = Icons.Default.Dock,
                        isChecked = dockedMode,
                        onCheckedChange = {
                            SpatialCameraPreferencesManager.setDockedMode(context, it)
                            AppSoundManager.playClick()
                        },
                        testTag = "bug_cam_dock_switch"
                    )

                    // 3. النقر السريع للالتقاط (Single-Tap Instant Snap)
                    SettingSwitchCard(
                        title = "الالتقاط الفوري بلمسة واحدة (Instant Snap)",
                        subtitle = "نقرة واحدة تلتقط لقطة الشاشة مباشرة وتفتح نموذج البلاغ بدون خطوات إضافية",
                        icon = Icons.Default.Bolt,
                        isChecked = tapInstantSnap,
                        onCheckedChange = {
                            SpatialCameraPreferencesManager.setTapInstantSnap(context, it)
                            AppSoundManager.playClick()
                        },
                        testTag = "bug_cam_instant_snap_switch"
                    )

                    // 4. تخطي الوميض الأبيض واستقرار الصورة (Anti-Flash Stabilization)
                    SettingSwitchCard(
                        title = "تخطي الوميض واستقرار اللقطة (Anti-Flash)",
                        subtitle = "تأخير ذكي مدته ثانية واحدة مع عداد تنازلي لإخفاء الزر والوميض قبل حفظ الصورة بنقاء",
                        icon = Icons.Default.FlashAuto,
                        isChecked = skipWhiteFlash,
                        onCheckedChange = {
                            SpatialCameraPreferencesManager.setSkipWhiteFlash(context, it)
                            AppSoundManager.playClick()
                        },
                        testTag = "bug_cam_anti_flash_switch"
                    )

                    // 5. الاهتزاز اللمسي (Haptic Feedback)
                    SettingSwitchCard(
                        title = "الاهتزاز اللمسي التفاعلي (Haptic Touch)",
                        subtitle = "توليد نبضة اهتزازية خفيفة عند لمس الزر أو تحريكه أو التقاط الشاشة",
                        icon = Icons.Default.Vibration,
                        isChecked = hapticEnabled,
                        onCheckedChange = {
                            SpatialCameraPreferencesManager.setHapticEnabled(context, it)
                            AppSoundManager.playClick()
                        },
                        testTag = "bug_cam_haptic_switch"
                    )

                    // 6. التحكم بدرجة الشفافية في وضع السكون (Idle Opacity)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .luxuryCardStyle(shapeRadius = 16.dp, borderAlpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Opacity, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("درجة الشفافية عند عدم الاستخدام", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("تخفيف حدة الزر لكي لا يشتت القراءة والإنتاج", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    0.35f to "خافت (35%)",
                                    0.65f to "متوازن (65%)",
                                    1.0f to "ساطع (100%)"
                                ).forEach { (alphaVal, label) ->
                                    val isSelected = (idleAlpha * 100).toInt() == (alphaVal * 100).toInt()
                                    Surface(
                                        onClick = {
                                            SpatialCameraPreferencesManager.setIdleAlpha(context, alphaVal)
                                            AppSoundManager.playClick()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) GoldPrimary.copy(alpha = 0.2f) else Color(0xFF131D2E),
                                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF223048))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) GoldPrimary else Color(0xFFCBD5E1),
                                                fontFamily = CairoFont,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 7. تثبيت الزر في الزوايا السريعة (Corner Snapping)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .luxuryCardStyle(shapeRadius = 16.dp, borderAlpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PinDrop, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("تثبيت موضع الزر فورياً في الزوايا", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("نقل الزر بلمسة واحدة لأي زاوية من الشاشة", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        SpatialCameraPreferencesManager.resetPosition(context)
                                        Toast.makeText(context, "تمت إعادة الزر لأسفل اليمين (الافتراضي) 📍", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.VerticalAlignBottom, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("الموضع الافتراضي", color = Color(0xFF00E5FF), fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE)
                                            .edit()
                                            .putFloat("spatial_dev_cam_offset_x", 40f)
                                            .putFloat("spatial_dev_cam_offset_y", 200f)
                                            .apply()
                                        Toast.makeText(context, "تم نقل الزر إلى أعلى اليسار 📍", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.VerticalAlignTop, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("أعلى الشاشة", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                                }
                            }
                        }
                    }

                    // 8. متابعة سجل البلاغات السريعة
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .luxuryCardStyle(shapeRadius = 16.dp, borderAlpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(GoldPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.BugReport, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("سجل بلاغاتي ومشاكلي المسجلة", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${myReports.size} بلاغاً مسجلاً في السحابة", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                                }
                            }

                            Button(
                                onClick = onOpenMyReports,
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ListAlt, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فتح السجل", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * مكون بطاقة التبديل الذكية للإعدادات بنمط HUD
 */
@Composable
private fun SettingSwitchCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 16.dp, borderAlpha = if (isChecked) 0.45f else 0.2f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isChecked) GoldPrimary.copy(alpha = 0.15f) else Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isChecked) GoldPrimary else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = GoldPrimary,
                    checkedTrackColor = GoldPrimary.copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF1E293B)
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
