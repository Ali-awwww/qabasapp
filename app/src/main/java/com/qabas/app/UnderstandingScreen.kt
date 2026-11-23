package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CutCornerShape
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.HudCornerDecor
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnderstandingScreen(
    inputText: String,
    contentType: String,
    contentTone: String,
    selectedRatio: String,
    videoDuration: String,
    videoStyleAnalysis: VideoStyleAnalysis?,
    styleDescription: String,
    onStyleDescriptionChange: (String) -> Unit,
    onEditIdea: () -> Unit,
    onProceed: () -> Unit
) {
    val theme = QabasTheme.current
    val analyticsContext = LocalContext.current
    LaunchedEffect(Unit) { AppServices.getAnalyticsService(analyticsContext).logScreenView("Understanding") }
    // جلب تنبؤي: يبدأ أثناء قراءة الفهم فيخفي زمن الشبكة عن مسار الإنتاج
    LaunchedEffect(inputText) {
        delay(350L)
        try { BrollPrefetch.prefetch(analyticsContext, inputText) } catch (_: Exception) {}
    }

    var hasViolation by remember { mutableStateOf(false) }
    val accountService = remember { AppServices.getAccountService(analyticsContext) }

    var isAnalyzing by remember { mutableStateOf(true) }
    var analysisResult by remember { mutableStateOf<IdeaAnalysis?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var selectedHook by remember { mutableStateOf<String?>(null) }
    var currentToneSelected by remember { mutableStateOf(contentTone.ifBlank { "خاشع وهادئ" }) }
    var currentVisualSelected by remember { 
        mutableStateOf(
            if (styleDescription.isNotBlank()) styleDescription 
            else if (videoStyleAnalysis != null && videoStyleAnalysis.detectedStyle.isNotBlank()) videoStyleAnalysis.detectedStyle 
            else "سينمائي"
        ) 
    }
    
    val goldGradient = Brush.horizontalGradient(colors = listOf(PresetAccent, PresetPrimary))
    val surfaceGradient = Brush.verticalGradient(colors = listOf(theme.cardSurface, theme.background))

    LaunchedEffect(inputText, contentType, contentTone, videoDuration) {
        isAnalyzing = true
        analysisError = null
        try {
            if (!ContentFilterService.filterText(inputText)) {
                SystemLogsManager.addLog(
                    "WARN",
                    "تحذير فلتر المحتوى — المتابعة بالتحليل والإنتاج",
                    theme.gold
                )
            }
            hasViolation = false
            val result = AppServices.analyzeIdea(inputText)
            
            if (result == null) {
                analysisError = "فشل التحليل: لم يتم الحصول على نتيجة من الخادم. تأكد من اتصال الإنترنت ومفتاح API."
                analysisResult = null
            } else {
                analysisResult = result
                analysisError = null
                if (result.tone.isNotBlank() && result.tone != "خاشع وملهم") currentToneSelected = result.tone
                
                val chosenStyle = when {
                    styleDescription.isNotBlank() -> styleDescription
                    videoStyleAnalysis != null && videoStyleAnalysis.detectedStyle.isNotBlank() -> videoStyleAnalysis.detectedStyle
                    else -> {
                        val bestAbsorbed = StyleBrain.chooseBestStyleForIdea(inputText, videoDuration.filter { it.isDigit() }.toIntOrNull() ?: 30, currentToneSelected, "الجمهور العام")
                        if (bestAbsorbed != null) "StyleBrain (${bestAbsorbed.name})" else "محايد (أساسي)"
                    }
                }
                currentVisualSelected = chosenStyle
                if (result.hookSuggestions.isNotEmpty() && result.hookSuggestions.firstOrNull()?.contains("تعذّر التحليل") != true) {
                    selectedHook = result.hookSuggestions.firstOrNull()
                }
                
                val fullStyleDesc = buildString {
                    append("الأسلوب: $chosenStyle")
                    append(" | النبرة: $currentToneSelected")
                    if (!selectedHook.isNullOrBlank()) {
                        append(", خطاف البداية: $selectedHook")
                    }
                }
                onStyleDescriptionChange(fullStyleDesc)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            analysisError = "حدث خطأ غير متوقع: ${e.message ?: "فشل التحليل"}"
            analysisResult = null
            SystemLogsManager.addLog("ERROR", "خطأ في UnderstandingScreen: ${e.message}", theme.destructive)
        } finally {
            isAnalyzing = false
        }
    }

    val updateCombinedStyle: (String, String, String?) -> Unit = { tone, visual, hook ->
        currentToneSelected = tone
        currentVisualSelected = visual
        selectedHook = hook
        val desc = buildString {
            append("الأسلوب: $visual, النبرة: $tone")
            if (!hook.isNullOrBlank()) {
                append(", خطاف البداية: $hook")
            }
        }
        onStyleDescriptionChange(desc)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Spatial HUD Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val primaryColor = PresetPrimary
            val stepX = 50.dp.toPx()
            val stepY = 50.dp.toPx()
            val w = size.width
            val h = size.height

            for (i in 0..(w / stepX).toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.05f),
                    start = Offset(i * stepX, 0f),
                    end = Offset(i * stepX, h),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
            for (j in 0..(h / stepY).toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.05f),
                    start = Offset(0f, j * stepY),
                    end = Offset(w, j * stepY),
                    strokeWidth = 0.5.dp.toPx()
                )
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
            TopAppBar(
                title = { 
                    Text(Translator.tr("الرؤية الإخراجية"), color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onEditIdea,
                        modifier = Modifier.padding(start = 8.dp).background(theme.cardSurface, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Translator.tr("العودة"), tint = PresetPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate.copy(alpha = 0.95f))
            )
        },
        bottomBar = {
            UnderstandingBottomBar(
                hasViolation = hasViolation,
                isAnalyzing = isAnalyzing,
                hasError = analysisError != null,
                goldGradient = goldGradient,
                onEditIdea = onEditIdea,
                onProceed = {
                    if (accountService.incrementVideoUsage()) {
                        onProceed()
                    } else {
                        android.widget.Toast.makeText(analyticsContext, Translator.tr("استنفدت الحد اليومي للفيديوهات!"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedContent(
                targetState = Triple(isAnalyzing, analysisResult, analysisError),
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                },
                label = "UnderstandingContent"
            ) { (analyzing, analysis, error) ->
                when {
                    analyzing -> {
                        UnderstandingLoadingView()
                    }
                    error != null -> {
                        UnderstandingErrorView(errorMessage = error, onRetry = onEditIdea)
                    }
                    analysis != null -> {
                        UnderstandingContentView(
                            inputText = inputText,
                            analysis = analysis,
                            surfaceGradient = surfaceGradient,
                            selectedTone = currentToneSelected,
                            selectedStyle = currentVisualSelected,
                            selectedHook = selectedHook,
                            onToneAndStyleChange = { t, s -> updateCombinedStyle(t, s, selectedHook) },
                            onSelectHook = { h -> updateCombinedStyle(currentToneSelected, currentVisualSelected, h) }
                        )
                    }
                    else -> {
                        UnderstandingErrorView(
                            errorMessage = "فشل التحليل: لم يتم الحصول على نتيجة صحيحة",
                            onRetry = onEditIdea
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun UnderstandingErrorView(errorMessage: String, onRetry: () -> Unit) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(theme.destructive.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = theme.destructive,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = Translator.tr("حدث خطأ في التحليل"),
            color = theme.textPrimary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = errorMessage,
            color = TextSecondary,
            fontFamily = NotoSansFont,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxSize(),
                colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                shape = CutCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // سهم العودة في RTL يشير يميناً: كان Icons.Default.ArrowBack
                    // (يسار) في كل التطبيق هو الوحيد غير المعكوس — الباقي
                    // AutoMirrored (56 موضعاً).
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        Translator.tr("العودة والمحاولة مرة أخرى"),
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HudCornerDecor(modifier = Modifier.matchParentSize(), color = PresetPrimary.copy(alpha = 0.2f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(CutCornerShape(12.dp))
                .background(theme.cardSurface),
            shape = CutCornerShape(12.dp),
            border = BorderStroke(1.dp, theme.secondarySurface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = Translator.tr("💡 نصيحة:"),
                    color = PresetPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Translator.tr("تأكد من:\n• وجود اتصال إنترنت\n• صحة مفتاح Gemini API\n• عدم تجاوز حد المستخدم اليومي"),
                    color = TextSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun UnderstandingBottomBar(
    hasViolation: Boolean,
    isAnalyzing: Boolean,
    hasError: Boolean,
    goldGradient: Brush,
    onEditIdea: () -> Unit,
    onProceed: () -> Unit
) {
    val theme = QabasTheme.current
    Surface(
        color = DeepSlate.copy(alpha = 0.98f),
        shadowElevation = 24.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onEditIdea,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.cardSurface),
                shape = CutCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.secondarySurface)
            ) {
                Text(Translator.tr("تعديل"), color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onProceed,
                enabled = !isAnalyzing && !hasViolation && !hasError,
                modifier = Modifier.weight(2f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                shape = CutCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (!isAnalyzing && !hasError) goldGradient else androidx.compose.ui.graphics.SolidColor(theme.secondarySurface),
                            CutCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Translator.tr("المتابعة للإعدادات"), color = DeepSlate, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun UnderstandingLoadingView() {
    val theme = QabasTheme.current
    var stepIndex by remember { mutableIntStateOf(0) }
    var currentProgress by remember { mutableFloatStateOf(0.15f) }

    val steps = remember {
        listOf(
            "فهم السياق واستلهام الفكرة الدعوية..." to 0.25f,
            "استخراج الكلمات المفتاحية والأدلة..." to 0.55f,
            "رسم وتنسيق المشاهد البصرية والخطاف..." to 0.85f,
            "إعداد الرؤية الإخراجية والأسلوب..." to 0.98f
        )
    }

    LaunchedEffect(Unit) {
        for (i in steps.indices) {
            stepIndex = i
            val targetP = steps[i].second
            val startP = currentProgress
            for (tick in 1..20) {
                currentProgress = startP + (targetP - startP) * (tick / 20f)
                delay(35)
            }
            delay(400)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        QabasGlowingSpinner(
            size = 84.dp,
            strokeWidth = 4.dp,
            color = PresetPrimary
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = Translator.tr("نستلهم الأفكار... ✦"),
            color = PresetPrimary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = steps[stepIndex.coerceIn(0, steps.size - 1)].first,
            color = theme.textPrimary,
            fontFamily = NotoSansFont,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Visual Progress Bar
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .border(1.dp, theme.secondarySurface, CutCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = CutCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                QabasProgressBar(
                    progress = currentProgress,
                    label = "تقدم التحليل الذكي",
                    subLabel = "يتم استخدام Gemini 1.5 لمعالجة السيناريو بأعلى معايير الإتقان",
                    height = 8.dp
                )
            }
        }
    }
}

@Composable
fun UnderstandingContentView(
    inputText: String,
    analysis: IdeaAnalysis,
    surfaceGradient: Brush,
    selectedTone: String,
    selectedStyle: String,
    selectedHook: String?,
    onToneAndStyleChange: (String, String) -> Unit,
    onSelectHook: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        IdeaSummaryCard(inputText, analysis)

        // Viral Hooks & CTAs Section
        ViralHooksSection(
            analysis = analysis,
            selectedHook = selectedHook,
            onSelectHook = onSelectHook
        )
        
        // Revolutionary Visual Selectors directly updating project state
        VisualSettingsSection(
            currentStyle = selectedStyle,
            currentTone = selectedTone,
            onToneAndStyleChange = onToneAndStyleChange
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoCard(
                title = Translator.tr("الهدف"),
                value = analysis.goal,
                icon = Icons.Default.Flag,
                modifier = Modifier.weight(1f)
            )
            InfoCard(
                title = Translator.tr("الجمهور"),
                value = analysis.targetAudience,
                icon = Icons.Default.Groups,
                modifier = Modifier.weight(1f)
            )
        }

        KeywordsCard(analysis.keywords)
        ScenesTimelineCard(analysis.proposedScenes)
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun ViralHooksSection(
    analysis: IdeaAnalysis,
    selectedHook: String?,
    onSelectHook: (String?) -> Unit
) {
    val theme = QabasTheme.current
    val hooks = if (analysis.hookSuggestions.isNotEmpty() && !analysis.hookSuggestions.any { it.contains("تعذّر التحليل") }) {
        analysis.hookSuggestions
    } else {
        emptyList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    Translator.tr("خطاف البداية المقترح للريلز (Viral Hook) ⚡"), 
                    color = PresetPrimary,
                    fontFamily = CairoFont, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 15.sp
                )
            }
            if (hooks.isNotEmpty()) {
                Surface(
                    color = theme.success.copy(alpha = 0.2f),
                    shape = CutCornerShape(8.dp)
                ) {
                    Text(
                        "${analysis.viralityScore}% ${Translator.tr("قوة الانتشار")}",
                        color = theme.success,
                        fontFamily = NotoSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (hooks.isEmpty()) {
            // مختبر الخطافات الاحتياطي: 3 خطافات محلية (سؤال/تناقض/أمر) بدل الرسالة الميتة
            val pack = remember(analysis.summary) { HookEngine.localPack(analysis.summary.ifBlank { "هذه الآية" }) }
            Text(
                Translator.tr("اختر أقوى افتتاحية لجذب انتباه المشاهد في أول ثانيتين:"),
                color = TextSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pack.hooks.forEach { hook ->
                    val isSelected = selectedHook == hook
                    Surface(
                        onClick = { onSelectHook(if (isSelected) null else hook) },
                        shape = CutCornerShape(12.dp),
                        color = if (isSelected) PresetPrimary.copy(alpha = 0.15f) else CardSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) PresetPrimary else theme.secondarySurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectHook(if (isSelected) null else hook) },
                                colors = RadioButtonDefaults.colors(selectedColor = PresetPrimary, unselectedColor = TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "« $hook »",
                                color = if (isSelected) PresetPrimary else TextPrimary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Text(
                    "🔁 الخاتمة الحلقية: ${pack.loopEnding}",
                    color = theme.success,
                    fontFamily = CairoFont,
                    fontSize = 12.sp
                )
            }
        } else {
            Text(
                Translator.tr("اختر أقوى افتتاحية لجذب انتباه المشاهد في أول 3 ثوانٍ:"),
                color = TextSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hooks.forEach { hook ->
                    val isSelected = selectedHook == hook
                    Surface(
                        onClick = { onSelectHook(if (isSelected) null else hook) },
                        shape = CutCornerShape(12.dp),
                        color = if (isSelected) PresetPrimary.copy(alpha = 0.15f) else CardSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isSelected) PresetPrimary else theme.secondarySurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectHook(if (isSelected) null else hook) },
                                colors = RadioButtonDefaults.colors(selectedColor = PresetPrimary, unselectedColor = TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "« $hook »",
                                color = if (isSelected) PresetPrimary else TextPrimary,
                                fontFamily = CairoFont,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IdeaSummaryCard(inputText: String, analysis: IdeaAnalysis) {
    val theme = QabasTheme.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(Translator.tr("الفكرة الأساسية"), color = PresetPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = CutCornerShape(12.dp),
            backgroundColor = CardSurface.copy(alpha = 0.85f),
            borderColor = PresetPrimary.copy(alpha = 0.2f)
        ) {
            HudCornerDecor(modifier = Modifier.matchParentSize(), color = PresetPrimary.copy(alpha = 0.1f))
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "« $inputText »",
                    color = theme.textPrimary,
                    fontSize = 16.sp,
                    fontFamily = NotoSansFont,
                    lineHeight = 28.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun VisualSettingsSection(
    currentStyle: String, 
    currentTone: String,
    onToneAndStyleChange: (String, String) -> Unit
) {
    val theme = QabasTheme.current
    var selectedTone by remember(currentTone) { mutableStateOf(currentTone) }
    var selectedStyle by remember(currentStyle) { mutableStateOf(currentStyle) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Visual Tone Selector
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(Translator.tr("المزاج العام (النبرة)"), color = theme.textPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VisualOptionCard(
                    title = Translator.tr("خاشع"),
                    icon = Icons.Default.NightlightRound,
                    color = Color(0xFF0B0F19), // Deep Slate
                    isSelected = selectedTone.contains("خاشع") || selectedTone.contains("هادئ"),
                    onClick = { 
                        selectedTone = "خاشع وهادئ"
                        onToneAndStyleChange(selectedTone, selectedStyle)
                    }
                )
                VisualOptionCard(
                    title = Translator.tr("ملحمي"),
                    icon = Icons.Default.LocalFireDepartment,
                    color = Color(0xFF3F1905), // Deep Orange
                    isSelected = selectedTone.contains("ملحمي") || selectedTone.contains("حماسي"),
                    onClick = { 
                        selectedTone = "ملحمي وحماسي"
                        onToneAndStyleChange(selectedTone, selectedStyle)
                    }
                )
                VisualOptionCard(
                    title = Translator.tr("حزين"),
                    icon = Icons.Default.WaterDrop,
                    color = Color(0xFF0A192F), // Deep Blue
                    isSelected = selectedTone.contains("حزين") || selectedTone.contains("مؤثر"),
                    onClick = { 
                        selectedTone = "حزين ومؤثر"
                        onToneAndStyleChange(selectedTone, selectedStyle)
                    }
                )
                VisualOptionCard(
                    title = Translator.tr("تأملي"),
                    icon = Icons.Default.Spa,
                    color = Color(0xFF162521), // Deep Green
                    isSelected = selectedTone.contains("تأملي"),
                    onClick = { 
                        selectedTone = "تأملي عميق"
                        onToneAndStyleChange(selectedTone, selectedStyle)
                    }
                )
            }
        }

        // Visual Style Selector - Powered by StyleBrain
        val activeAbsorbed = StyleBrain.getDefaultStyle()
        val allAbsorbed = StyleBrain.getAllAbsorbedStyles()
        val coreScore = StyleBrain.getCoreStyleStrength()

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        Translator.tr("التوجيه الإخراجي بواسطة StyleBrain"),
                        color = theme.textPrimary,
                        fontFamily = TajawalFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Surface(
                    color = PresetPrimary.copy(alpha = 0.15f),
                    shape = CutCornerShape(8.dp),
                    border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.4f))
                ) {
                    Text(
                        "تلقائي ذكي ✦",
                        color = PresetPrimary,
                        fontSize = 11.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // StyleBrain Director Active Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = theme.cardSurface),
                border = BorderStroke(1.5.dp, PresetPrimary),
                shape = CutCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🧠 الأسلوب المعتمد: ${activeAbsorbed.name}",
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "القوة: $coreScore%",
                            color = theme.success,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        "يقوم عقل الأساليب تلقائياً بتطبيق سمات الأسلوب المستنسخ (ألوان داكنة مع ذهبي، حركة زووم بطيء...)",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    if (allAbsorbed.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allAbsorbed.forEach { absorbed ->
                                val isChosen = selectedStyle.contains(absorbed.name) || (selectedStyle.contains("StyleBrain") && absorbed.name == activeAbsorbed.name)
                                Surface(
                                    color = if (isChosen) PresetPrimary.copy(alpha = 0.2f) else theme.background,
                                    shape = CutCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isChosen) PresetPrimary else theme.secondarySurface),
                                    modifier = Modifier.clickable {
                                        selectedStyle = "StyleBrain (${absorbed.name})"
                                        onToneAndStyleChange(selectedTone, selectedStyle)
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        if (isChosen) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            absorbed.name,
                                            color = if (isChosen) PresetPrimary else TextSecondary,
                                            fontSize = 11.sp,
                                            fontFamily = CairoFont,
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
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
}

@Composable
fun VisualOptionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    val theme = QabasTheme.current
        GlassmorphicSurface(
            modifier = Modifier
                .width(100.dp)
                .height(110.dp)
                .clickable { onClick() },
            shape = CutCornerShape(12.dp),
            backgroundColor = color.copy(alpha = 0.8f),
            borderColor = if (isSelected) PresetPrimary else Color.Transparent
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.size(48.dp).background(theme.textPrimary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = if (isSelected) PresetPrimary else theme.textPrimary.copy(alpha=0.7f), modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(title, color = if (isSelected) PresetPrimary else theme.textPrimary, fontSize = 14.sp, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold)
            }
        }
}

@Composable
fun InfoCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val theme = QabasTheme.current
    Column(
        modifier = modifier
            .clip(CutCornerShape(16.dp))
            .background(theme.cardSurface)
            .border(1.dp, theme.secondarySurface, CutCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(40.dp).background(DeepSlate, CircleShape).border(1.dp, PresetPrimary.copy(alpha=0.3f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, color = TextSecondary, fontSize = 12.sp, fontFamily = NotoSansFont)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = theme.textPrimary, fontSize = 14.sp, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordsCard(keywords: List<String>) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CutCornerShape(16.dp))
            .background(theme.cardSurface)
            .border(1.dp, theme.secondarySurface, CutCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tag, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(Translator.tr("الكلمات المفتاحية"), color = theme.textPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        GlassmorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = CutCornerShape(12.dp),
            backgroundColor = CardSurface.copy(alpha = 0.85f),
            borderColor = theme.secondarySurface
        ) {
            HudCornerDecor(modifier = Modifier.matchParentSize(), color = PresetPrimary.copy(alpha = 0.05f))
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keywords.forEach { keyword ->
                    Box(
                        modifier = Modifier
                            .background(theme.background, CutCornerShape(20.dp))
                            .border(1.dp, PresetPrimary.copy(alpha = 0.5f), CutCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(keyword, color = PresetPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = NotoSansFont)
                    }
                }
            }
        }
    }
}

@Composable
fun ScenesTimelineCard(scenes: List<String>) {
    val theme = QabasTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MovieFilter, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(Translator.tr("المشاهد المقترحة"), color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        
        Column(modifier = Modifier.padding(end = 8.dp)) {
            scenes.forEachIndexed { index, sceneTitle ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Timeline visual
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (index == 0) PresetPrimary.copy(alpha=0.15f) else DeepSlate, CircleShape)
                                .border(2.dp, PresetPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index == 0) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(18.dp))
                            } else {
                                Text("${index + 1}", color = PresetPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (index < scenes.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .weight(1f)
                                    .background(PresetPrimary.copy(alpha = 0.3f))
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Scene Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (index < scenes.size - 1) 24.dp else 0.dp)
                            .clip(CutCornerShape(12.dp))
                            .background(CardSurface)
                            .border(1.dp, theme.secondarySurface, CutCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(sceneTitle, color = TextPrimary, fontSize = 16.sp, fontFamily = TajawalFont, lineHeight = 24.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            IslamicSourceAttributionBadge(text = sceneTitle)
                        }
                    }
                }
            }
        }
    }
}
