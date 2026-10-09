package com.qabas.app

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.qabas.app.ui.components.GlassmorphicSurface

/**
 * شاشة بداية وتحميل بيانات الاستوديو المطورة (Cybernetic Spatial Splash & HUD Loading).
 * تعرض هوية قبس ستوديو الفضائية المتطورة مع أنميشن الشعار الكوانتي وشريط المعالجة الليزري.
 */
@Composable
fun DataLoadingScreen(
    onFinished: () -> Unit,
    onLoadProjects: suspend () -> Unit = {}
) {
    val appContext = LocalContext.current.applicationContext
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (finished) return
        finished = true
        onFinished()
    }

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()
    val isCyberHud = QabasTheme.current.isCyberHud

    // أنميشن الدخول المتدرج (Staggered Intro Animation)
    var isScreenRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        isScreenRevealed = true
    }

    val introAlpha by animateFloatAsState(
        targetValue = if (isScreenRevealed) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "introAlpha"
    )
    val introScale by animateFloatAsState(
        targetValue = if (isScreenRevealed) 1f else 0.92f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "introScale"
    )

    data class LoadStep(val title: String, val subtitle: String, val icon: ImageVector)

    val steps = remember {
        listOf(
            LoadStep("تهيئة الخدمات ومحركات الاستدلال", "CORE RUNTIME & AI ENGINES", Icons.Default.Settings),
            LoadStep("مزامنة السحابة والتحقق الأمني", "CLOUD VAULT & SUPABASE SYNC", Icons.Default.CloudDownload),
            LoadStep("تحميل مسارات الإنتاج والمشاريع", "WORKSPACE & ASSETS MATRIX", Icons.Default.Folder)
        )
    }

    var currentStep by remember { mutableIntStateOf(0) }
    var overallProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("جاري تشغيل مصفوفة قبس...") }

    // انيميشن النبض
    val infiniteTransition = rememberInfiniteTransition(label = "SplashPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // وميض شعاع الليزر
    val laserGlowX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laserGlowX"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "progressAnim"
    )

    LaunchedEffect(Unit) {
        currentStep = 0
        statusText = steps[0].title
        overallProgress = 0.10f
        
        // --- Step 1: Local Services ---
        try {
            AppServices.init(appContext)
            AudioPlayerManager.init(appContext)
            BillingManager.getInstance(appContext)
            StyleBrain.init(appContext)
            StyleManager.init(appContext)
            overallProgress = 0.40f
        } catch (e: Exception) {
            Log.e("DataLoading", "Local init failed: ${e.message}")
        }
        delay(400)

        // --- Step 2: Cloud & Auth ---
        currentStep = 1
        statusText = steps[1].title
        try {
            CloudServices.tryInitFromSavedConfig(appContext)
            if (SupabaseServices.isSupabaseAvailable) {
                CloudServices.syncAdminClaim(appContext)
                SupabaseServices.Auth.refreshVerifiedRole()
            }
            overallProgress = 0.75f
        } catch (e: Exception) {
            Log.e("DataLoading", "Cloud init failed: ${e.message}")
        }
        delay(400)

        // --- Step 3: Projects & Data ---
        currentStep = 2
        statusText = steps[2].title
        try {
            onLoadProjects()
            if (SupabaseServices.isSupabaseAvailable) {
                SupabaseServices.Database.ping()
            }
            overallProgress = 0.98f
        } catch (e: Exception) {
            Log.e("DataLoading", "Data load failed: ${e.message}")
        }
        
        overallProgress = 1f
        statusText = "اكتملت جاهزية النظام بنجاح ⚡"
        delay(600)
        finish()
    }

    LaunchedEffect(Unit) {
        delay(10000)
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate),
        contentAlignment = Alignment.Center
    ) {
        // 1. الخلفية السينمائية مع طبقة تشويش هندسية
        Image(
            painter = painterResource(id = R.drawable.img_qabas_splash),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.22f
        )

        // 2. شبكة الإحداثيات الهندسية (HUD Grid)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = 40.dp.toPx()
            val stepY = 40.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            
            // خطوط طولية
            for (i in 0..(w / stepX).toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.03f),
                    start = Offset(i * stepX, 0f),
                    end = Offset(i * stepX, h),
                    strokeWidth = 1f
                )
            }
            // خطوط عرضية
            for (i in 0..(h / stepY).toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.03f),
                    start = Offset(0f, i * stepY),
                    end = Offset(w, i * stepY),
                    strokeWidth = 1f
                )
            }
        }

        // 3. تدرج لوني عميق زجاجي هولوغرافي
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DeepSlate.copy(alpha = 0.65f),
                            DeepSlate.copy(alpha = 0.85f),
                            DeepSlate
                        )
                    )
                )
        )

        // 4. عناصر HUD تزيينية في الزوايا
        HudCornerDecor(modifier = Modifier.fillMaxSize(), color = primaryColor.copy(alpha = 0.5f))

        // 5. Watermark Background Text
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "QABAS",
                color = Color.White.copy(alpha = 0.02f),
                fontSize = 180.sp,
                fontWeight = FontWeight.Black,
                fontFamily = RobotoMonoFont,
                letterSpacing = 12.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = introAlpha
                    translationY = (1f - introAlpha) * 40f
                    scaleX = introScale
                    scaleY = introScale
                }
        ) {
            // شارة الحالة التكنولوجية العلوية (Geometric HUD Badge)
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    color = primaryColor.copy(alpha = 0.08f),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .padding(bottom = 20.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            ambientColor = primaryColor,
                            spotColor = primaryColor
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isCyberHud) "SYSTEM CORE // ACTIVE MATRIX" else "QABAS STUDIO // SPATIAL OS",
                            color = primaryColor,
                            fontFamily = RobotoMonoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // الشعار الرسمي الفاخر بنظام الحركة الكوانتية
            CyberneticAnimatedLogo(
                size = 144.dp,
                showOrbitalHud = true,
                showCosmicGlow = true,
                showLightSweep = true,
                showLevitation = true,
                showCornerBrackets = true,
                showParticles = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "قَــبَــس",
                color = primaryColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = CairoFont,
                letterSpacing = 4.sp
            )

            Text(
                text = "استوديو صناعة الأثر بالذكاء الاصطناعي",
                color = TextPrimary.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // لوحة الـ HUD الزجاجية الهندسية (Geometric Glass Panel)
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                primaryColor.copy(alpha = 0.6f),
                                Color.Transparent,
                                primaryColor.copy(alpha = 0.6f)
                            )
                        ),
                        shape = CutCornerShape(16.dp)
                    ),
                shape = CutCornerShape(16.dp),
                backgroundColor = DeepSlate.copy(alpha = 0.85f),
                borderColor = primaryColor.copy(alpha = 0.25f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // شريط التقدم الليزري الخطي (Linear Minimalist Progress)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "وضع التشغيل: مُستقر",
                                    color = TextSecondary.copy(alpha = 0.65f),
                                    fontFamily = CairoFont,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = statusText,
                                    color = primaryColor,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                color = accentColor,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                        }

                        // مسار الليزر الهندسي
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedProgress)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(accentColor, primaryColor)
                                        )
                                    )
                            )
                            
                            // وميض المسح الضوئي
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(40.dp)
                                    .offset(x = (laserGlowX * 300).dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent)
                                        )
                                    )
                            )
                        }
                    }

                    // قائمة الخطوات (Minimalist Tech List)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        steps.forEachIndexed { index, step ->
                            val isDone = index < currentStep || overallProgress >= 1f
                            val isActive = index == currentStep && overallProgress < 1f

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(
                                        if (isActive) primaryColor.copy(alpha = 0.05f) else Color.Transparent
                                    )
                                    .drawBehind {
                                        if (isActive) {
                                            drawLine(
                                                color = primaryColor,
                                                start = Offset(0f, 0f),
                                                end = Offset(0f, this.size.height),
                                                strokeWidth = 3.dp.toPx()
                                            )
                                        }
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isDone) Icons.Default.Check else step.icon,
                                    contentDescription = null,
                                    tint = when {
                                        isDone -> primaryColor
                                        isActive -> Color.White
                                        else -> TextSecondary.copy(alpha = 0.3f)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = step.title,
                                        color = if (isActive || isDone) Color.White else TextSecondary.copy(alpha = 0.5f),
                                        fontFamily = CairoFont,
                                        fontWeight = if (isActive || isDone) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = step.subtitle,
                                        color = if (isActive) accentColor else TextSecondary.copy(alpha = 0.45f),
                                        fontFamily = RobotoMonoFont,
                                        fontSize = 8.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                                
                                if (isActive) {
                                    QabasGlowingSpinner(size = 14.dp, strokeWidth = 1.5.dp)
                                }
                            }
                        }
                    }

                    // شريط التيليمتري (Geometric Data Strip)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KERN: 0x8F2 // MEM: 1.2GB",
                            color = primaryColor.copy(alpha = 0.7f),
                            fontFamily = RobotoMonoFont,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SECURE PROTOCOL V4",
                            color = accentColor.copy(alpha = 0.7f),
                            fontFamily = RobotoMonoFont,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ENGINE STATE: OPERATIONAL // SYNC: ENCRYPTED",
                color = primaryColor.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontFamily = RobotoMonoFont,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

