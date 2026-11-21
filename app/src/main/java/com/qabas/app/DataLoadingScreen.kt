package com.qabas.app

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DataLoadingScreen(
    onFinished: () -> Unit,
    onLoadProjects: suspend () -> Unit = {}
) {
    val theme = QabasTheme.current
    val appContext = LocalContext.current.applicationContext
    var isExiting by remember { mutableStateOf(false) }
    val exitAlpha by animateFloatAsState(
        targetValue = if (isExiting) 0f else 1f,
        animationSpec = tween(320, easing = FastOutLinearInEasing),
        label = "exitAlpha"
    )
    fun finish() {
        if (isExiting) return
        isExiting = true
    }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            delay(320)
            onFinished()
        }
    }

    var isScreenRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        isScreenRevealed = true
    }

    val introAlpha by animateFloatAsState(
        targetValue = if (isScreenRevealed) 1f else 0f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "introAlpha"
    )

    data class LoadStep(val title: String, val subtitle: String, val icon: ImageVector)

    val steps = remember {
        listOf(
            LoadStep("تهيئة الخدمات المحلية والنظام", "المشغّل، الأنماط، وإعدادات الحساب", Icons.Default.Settings),
            LoadStep("مزامنة السحابة والتحقق الأمني", "التحقق من حالة الجلسة عبر Supabase", Icons.Default.CloudDownload),
            LoadStep("تحميل مسارات الإنتاج والمشاريع", "تجهيز مساحة العمل والأعمال المحفوظة", Icons.Default.Folder)
        )
    }

    var currentStep by remember { mutableIntStateOf(0) }
    var overallProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("جاري تجهيز مساعد المسلم...") }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "progressAnim"
    )

    LaunchedEffect(retryToken) {
        loadError = null
        currentStep = 0
        statusText = steps[0].title
        overallProgress = 0.15f

        try {
            withContext(Dispatchers.IO) {
                AppServices.init(appContext)
                AudioPlayerManager.init(appContext)
                BillingManager.getInstance(appContext)
                StyleBrain.init(appContext)
                StyleManager.init(appContext)
            }
            overallProgress = 0.45f
        } catch (e: Exception) {
            Log.e("DataLoading", "Local init failed: ${e.message}")
            loadError = "تعذر تهيئة خدمات التطبيق المحلية. حاول مرة أخرى."
        }

        currentStep = 1
        statusText = steps[1].title
        try {
            withContext(Dispatchers.IO) {
                CloudServices.tryInitFromSavedConfig(appContext)
                if (SupabaseServices.isSupabaseAvailable) {
                    CloudServices.syncAdminClaim(appContext)
                    SupabaseServices.Auth.refreshVerifiedRole()
                }
            }
            overallProgress = 0.78f
        } catch (e: Exception) {
            Log.e("DataLoading", "Cloud init failed: ${e.message}")
            loadError = loadError ?: "تعذر مزامنة الخدمات السحابية. تحقق من الاتصال وحاول مرة أخرى."
        }

        currentStep = 2
        statusText = steps[2].title
        try {
            withContext(Dispatchers.IO) {
                onLoadProjects()
                if (SupabaseServices.isSupabaseAvailable) {
                    SupabaseServices.Database.ping()
                }
            }
            overallProgress = 0.98f
        } catch (e: Exception) {
            Log.e("DataLoading", "Data load failed: ${e.message}")
            loadError = loadError ?: "تعذر تحميل المشاريع. حاول مرة أخرى."
        }

        if (loadError == null) {
            overallProgress = 1f
            statusText = "اكتملت جاهزية النظام بنجاح"
            finish()
        } else {
            statusText = "توقفت التهيئة وتحتاج إلى إعادة المحاولة"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .graphicsLayer { alpha = exitAlpha },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = introAlpha }
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = theme.primary.copy(alpha = if (theme.isDark) 0.18f else 0.12f),
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "شعار مساعد المسلم",
                        tint = theme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "مساعد المسلم",
                color = theme.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CairoFont
            )

            Text(
                text = "استوديو صناعة الأثر والمحتوى الإسلامي",
                color = theme.textSecondary,
                fontSize = 13.5.sp,
                fontFamily = NotoSansFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            AppleCard(cornerRadius = 18.dp) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تهيئة النظام",
                                color = theme.textSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                            Text(
                                text = statusText,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            color = theme.primary,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = theme.primary,
                        trackColor = theme.secondarySurface
                    )

                    Column {
                        steps.forEachIndexed { index, step ->
                            val isDone = loadError == null && (index < currentStep || overallProgress >= 1f)
                            val isActive = index == currentStep && overallProgress < 1f && loadError == null

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isDone -> theme.success.copy(alpha = 0.14f)
                                                isActive -> theme.primary.copy(alpha = 0.14f)
                                                else -> theme.secondarySurface
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isActive) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = theme.primary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isDone) Icons.Default.CheckCircle else step.icon,
                                            contentDescription = null,
                                            tint = if (isDone) theme.success else theme.textSecondary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = step.title,
                                        color = if (isDone || isActive) theme.textPrimary else theme.textSecondary,
                                        fontFamily = CairoFont,
                                        fontWeight = if (isDone || isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = step.subtitle,
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (index < steps.lastIndex) {
                                AppleInsetDivider()
                            }
                        }
                    }
                }
            }

            if (loadError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = loadError.orEmpty(),
                    color = theme.destructive,
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                ApplePrimaryButton(
                    text = "إعادة المحاولة",
                    icon = Icons.Default.Refresh,
                    onClick = { retryToken++ }
                )
            }
        }
    }
}
