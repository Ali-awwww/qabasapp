package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.qabas.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSmartDirectorScreen(
    onBack: () -> Unit,
    onMakeMagic: () -> Unit,
    brainViewModel: QabasBrainViewModel = viewModel(factory = QabasBrainViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val tierManager = remember { AppServices.getTierStateManager(context) }
    val canUseDirector by remember { mutableStateOf(tierManager.canGenerateVideo()) }
    
    var scriptText by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableIntStateOf(30) }
    var selectedTone by remember { mutableStateOf("وقور وملهم") }
    var selectedAudience by remember { mutableStateOf("الجمهور العام") }
    var showProDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: التوليد والإخراج، 1: سجل قرارات العقل
    
    val uiState by brainViewModel.uiState.collectAsState()
    val historyItems by brainViewModel.decisionHistory.collectAsState()
    val isProcessing = uiState.status != BrainStatus.IDLE && uiState.status != BrainStatus.SUCCESS
    val decision = uiState.currentDecision

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    if (showProDialog) {
        ModalBottomSheet(
            onDismissRequest = { showProDialog = false },
            containerColor = DeepSlate,
            scrimColor = DeepSlate.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = QabasGold, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "اشترك في خادم المسلم برو 👑",
                    color = TextPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "ميزة المخرج الذكي وذاكرة كائنات الأنماط من Firestore تتيح لك إخراجاً سينمائياً فائق الجودة بالذكاء الاصطناعي.",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                ProFeatureRow(Icons.Default.AutoAwesome, "ربط حي بذاكرة كائنات الأنماط (Firestore)")
                ProFeatureRow(Icons.Default.Psychology, "استدلال سينمائي متقدم عبر Gemini API")
                ProFeatureRow(Icons.Default.MovieFilter, "توليد فلاتر وتوجيهات FFmpeg للمونتاج الحقيقي")
                ProFeatureRow(Icons.Default.Tune, "تعلم تراكمي من تقييماتك للمخرجات")
                
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            showProDialog = false
                            snackbarHostState.showSnackbar("تم تفعيل صلاحيات المخرج الذكي مؤقتاً للاختبار")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("تجربة المخرج الذكي الآن", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Translator.tr("المخرج الذكي والعقل"), fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        com.qabas.app.ui.components.ModelIndicator()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PresetPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        },
        containerColor = DeepSlate
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CardSurface,
                contentColor = PresetPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PresetPrimary,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("المخرج والإنتاج", fontFamily = CairoFont, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("سجل قرارات العقل", fontFamily = CairoFont, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                            if (historyItems.isNotEmpty()) {
                                Surface(
                                    color = if (selectedTab == 1) PresetPrimary else ThemeSeparator,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${historyItems.size}",
                                        color = if (selectedTab == 1) DeepSlate else TextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
            // بطاقة حالة العقل والذاكرة السحابية
            item {
                BrainNeuralStateCard(
                    uiState = uiState,
                    onSyncMemory = {
                        brainViewModel.syncBrainMemory()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("تمت مزامنة ذاكرة الأنماط مع سحابة Firestore")
                        }
                    }
                )
            }

            item {
                Text(
                    "أدخل فكرة الفيديو أو الآية أو القصة، وسيقوم 'العقل' باستدعاء الأنماط المرجعية المحفوظة من Firestore وصياغة السيناريو والإخراج بـ Gemini API.",
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp
                )
            }

            // حقل إدخال الفكرة
            item {
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { scriptText = it },
                    label = { Text("مثال: تدبر سورة الضحى وكيف يبعث الأمل في القلوب الحزينة", fontFamily = CairoFont, color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PresetPrimary,
                        unfocusedBorderColor = ThemeSecondarySurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // خيارات النبرة والمدة السريعة
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("نبرة الإخراج المستهدفة:", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("وقور وملهم", "وثائقي سينمائي", "حماسي دعوي", "هادئ وتأملي").forEach { tone ->
                            val isSelected = selectedTone == tone
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedTone = tone },
                                color = if (isSelected) PresetPrimary else CardSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) PresetPrimary else ThemeSecondarySurface),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = tone,
                                    color = if (isSelected) DeepSlate else TextSecondary,
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // زر التوليد واتخاذ القرار بواسطة العقل
            item {
                Button(
                    onClick = {
                        if (scriptText.isNotBlank()) {
                            if (!canUseDirector) {
                                showProDialog = true
                            } else {
                                brainViewModel.processIdeaWithBrain(
                                    idea = scriptText,
                                    durationSeconds = durationSeconds,
                                    tonePreference = selectedTone,
                                    targetAudience = selectedAudience,
                                    onSuccess = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("اكتمل اتخاذ القرار الإخراجي بنجاح!")
                                        }
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isProcessing && scriptText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isProcessing) 
                                    Brush.horizontalGradient(listOf(ThemeSeparator, ThemeSecondarySurface))
                                else 
                                    Brush.horizontalGradient(listOf(PresetAccent, PresetPrimary))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LottieBrainVisualizer(
                                    status = uiState.status,
                                    size = 28.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    uiState.statusMessage, 
                                    color = TextPrimary,
                                    fontFamily = CairoFont, 
                                    fontSize = 14.sp, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "تشغيل العقل واستدعاء الذاكرة 🧠", 
                                    color = DeepSlate, 
                                    fontFamily = CairoFont, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // عرض القرار الإخراجي الحقيقي الصادر من العقل
            if (decision != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // شريط ملخص القرار والنمط المعتمد
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Stars, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                                        Text(
                                            decision.title,
                                            color = PresetPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }

                                    Surface(
                                        color = ThemeSuccess.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, ThemeSuccess)
                                    ) {
                                        Text(
                                            "توافق: ${decision.compatibilityScore}%",
                                            color = ThemeSuccess,
                                            fontFamily = CairoFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    "النمط المرجعي المطبق: ${decision.selectedStyleName}",
                                    color = TextSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )

                                Text(
                                    decision.aiRationale,
                                    color = TextPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        // الخطاف البصري (Hook)
                        DirectorResultCard(
                            icon = Icons.Default.FlashOn,
                            title = "الخطاف البصري والسمعي (Hook)",
                            content = decision.hook
                        )

                        // المشاهد المولدة من العقل
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ThemeSecondarySurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = PresetPrimary)
                                    Text("المشاهد الإخراجية والسكريبت (${decision.scriptScenes.size} مشاهد)", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }

                                decision.scriptScenes.forEachIndexed { idx, sc ->
                                    Surface(
                                        color = CardSurface,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(sc.title, color = PresetAccent, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("${sc.durationInSeconds} ثوانٍ", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                                            }
                                            Text(sc.description, color = TextPrimary, fontFamily = CairoFont, fontSize = 12.5.sp, lineHeight = 18.sp)
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text("التأثير: ${sc.visualEffect}", color = PresetPrimary, fontFamily = CairoFont, fontSize = 11.sp)
                                                Text("الانتقال: ${sc.transitionType}", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // التوجيه البصري وفلاتر FFmpeg
                        DirectorResultCard(
                            icon = Icons.Default.Palette,
                            title = "التوجيه البصري والألوان",
                            content = "${decision.visualDirectives}\n(اللون الرئيسي: ${decision.primaryColorHex} | الخلفية: ${decision.backgroundColorHex})"
                        )

                        // فلاتر FFmpeg المولدة من العقل
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ThemeSecondarySurface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(18.dp))
                                    Text("سلسلة فلاتر FFmpeg الجاهزة للمونتاج", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text(
                                    decision.ffmpegFilterSnippet,
                                    color = TextSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier
                                        .background(DeepSlate, RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                        .fillMaxWidth()
                                )
                            }
                        }

                        // حلقة التغذية الراجعة لتعزيز ذكاء العقل
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSurface, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("هل ناسبك قرار العقل الإخراجي؟", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = {
                                        brainViewModel.recordDecisionFeedback(true)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("شكراً! تم تعزيز وزن النمط في ذاكرة العقل.")
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).background(ThemeSuccess.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(Icons.Default.ThumbUp, contentDescription = "Like", tint = ThemeSuccess, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        brainViewModel.recordDecisionFeedback(false)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("تم تسجيل الملاحظة لتعديل الأوزان في Firestore.")
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).background(ThemeDestructive.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(Icons.Default.ThumbDown, contentDescription = "Dislike", tint = ThemeDestructive, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // زر الانتقال للمونتاج الفعلي
                        Button(
                            onClick = onMakeMagic,
                            modifier = Modifier.fillMaxWidth().height(58.dp).shadow(8.dp, RoundedCornerShape(16.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeSuccess),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.MovieCreation, contentDescription = null, tint = DeepSlate)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("اعتماد القرار والانتقال للمونتاج 🎬", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    } else {
        BrainDecisionHistoryLog(
            historyItems = historyItems,
            modifier = Modifier.fillMaxSize(),
            onApplyDecisionToProject = { historyItem ->
                scriptText = historyItem.ideaInput
                selectedTone = historyItem.tone
                selectedTab = 0
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("تم استرجاع مدخلات القرار '${historyItem.decision.title}' بنجاح")
                }
            },
            onFeedback = { itemId, isPos ->
                brainViewModel.recordHistoryFeedback(itemId, isPos)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(if (isPos) "تم تعزيز وزن كائن النمط في الذاكرة" else "تم تسجيل الملاحظة لخفض وزن النمط")
                }
            },
            onDeleteItem = { itemId ->
                brainViewModel.deleteHistoryItem(itemId)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("تم حذف القرار من السجل")
                }
            },
            onClearAll = {
                brainViewModel.clearDecisionHistory()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("تم مسح سجل قرارات العقل بالكامل")
                }
            }
        )
    }
}
}
}

@Composable
private fun ProFeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
        Text(text, color = TextPrimary, fontFamily = CairoFont, fontSize = 13.5.sp)
    }
}

@Composable
private fun DirectorResultCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ThemeSecondarySurface)
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                Text(content, color = TextSecondary, fontFamily = CairoFont, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}
