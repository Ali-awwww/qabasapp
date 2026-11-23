package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleBrainSection(
    context: Context,
    brainViewModel: QabasBrainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = QabasBrainViewModelFactory(context)
    )
) {
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    var videoPath by remember { mutableStateOf("") }
    var styleName by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var saveToCloud by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedVideoLabel by remember { mutableStateOf<String?>(null) }
    var selectedRequestId by remember { mutableStateOf<String?>(null) }

    var primaryStyleForFusion by remember { mutableStateOf<AbsorbedStyle?>(null) }
    var secondaryStyleForFusion by remember { mutableStateOf<AbsorbedStyle?>(null) }
    var blendRatio by remember { mutableFloatStateOf(0.5f) }
    var customFusedName by remember { mutableStateOf("") }

    var customMasterName by remember { mutableStateOf("") }
    var showHardResetConfirm by remember { mutableStateOf(false) }

    val styles by StyleBrain.absorbedStyles.collectAsState()
    val primaryStyleId by StyleBrain.primaryStyleId.collectAsState()
    val masterStyles by StyleManager.masterStyles.collectAsState()
    val activeMasterStyle by StyleManager.activeMasterStyle.collectAsState()
    val coreStyle by StyleBrain.coreStyle.collectAsState()
    val isContinuousLearningEnabled by StyleBrain.isContinuousLearningEnabled.collectAsState()
    val improvementProposals by StyleBrain.improvementProposals.collectAsState()
    val lastLearningTimestamp by StyleBrain.lastLearningTimestamp.collectAsState()
    val isAnalyzingLearning by StyleBrain.isAnalyzingLearning.collectAsState()

    val absorptionState by brainViewModel.absorptionState.collectAsState()
    val isApiKeyAvailable by brainViewModel.isApiKeyAvailable.collectAsState()

    val isFirebaseActive = CloudServices.isFirebaseInitialized

    // فحص المفتاح وإصلاح فوري عند فتح الشاشة: صفر القوة الوهمية + عرض صحيح
    LaunchedEffect(Unit) {
        brainViewModel.checkApiKeyAvailability()
        withContext(Dispatchers.IO) {
            StyleBrainLoadFix.repairPersistedEmptyCore(context)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "style_brain_videos").apply { mkdirs() }
                    val outFile = File(dir, "ref_${System.currentTimeMillis()}.mp4")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    } ?: return@withContext null
                    outFile
                }
                if (cached != null && cached.exists() && cached.length() > 0L) {
                    videoPath = cached.absolutePath
                    selectedVideoLabel = uri.lastPathSegment ?: cached.name
                    message = "تم اختيار الفيديو: ${selectedVideoLabel} (${cached.length() / 1024} ك.ب)"
                } else {
                    message = "تعذّر قراءة ملف الفيديو المختار"
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                message = "فشل اختيار الفيديو: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    // قوة معروضة بأمان (0–100) بدون فرض 50
    val strength = coreStyle.strengthScore.coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(theme.cardSurface, DeepSlate)))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isFirebaseActive) theme.success.copy(alpha = 0.15f) else theme.textTertiary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isFirebaseActive) theme.success else theme.textTertiary)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isFirebaseActive) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isFirebaseActive) theme.success else theme.textTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isFirebaseActive) "Firestore سحابي نشط" else "ذاكرة محلية فقط",
                                color = if (isFirebaseActive) theme.success else theme.textTertiary,
                                fontSize = 10.sp,
                                fontFamily = TajawalFont
                            )
                        }
                    }

                    Row {
                        // زر التصفير الكامل
                        IconButton(
                            onClick = { showHardResetConfirm = true },
                            enabled = !absorptionState.isAbsorbing && !isSyncing
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "تصفير العقل", tint = theme.destructive)
                        }
                        IconButton(
                            onClick = {
                                isSyncing = true
                                coroutineScope.launch {
                                    val syncedStyles = StyleBrain.syncCloudStylesWithLocal(context)
                                    val syncedMasters = StyleManager.syncWithCloud(context)
                                    isSyncing = false
                                    Toast.makeText(
                                        context,
                                        "تمت مزامنة (${syncedStyles.size}) أنماط و (${syncedMasters.size}) ملفات ماستر ☁️",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PresetPrimary)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = "مزامنة", tint = PresetPrimary)
                            }
                        }
                    }
                }

                LottieBrainVisualizer(
                    status = if (isSyncing) BrainStatus.FETCHING_MEMORY else BrainStatus.IDLE,
                    size = 54.dp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("قوة عقل خادم المسلم التراكمي (QabasCoreStyle)", color = TextSecondary, fontFamily = CairoFont, fontSize = 14.sp)
                // عرض صريح LTR لتجنب قلب الأرقام في RTL
                Text(
                    text = "$strength / 100",
                    color = PresetPrimary,
                    fontFamily = CairoFont,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                )
                Text(
                    if (styles.isEmpty() && strength == 0) "عقل فارغ تماماً — لم يُمتص أي أسلوب بعد"
                    else if (styles.isEmpty()) "هوية أساسية — لم يُستخرج نمط من فيديو بعد"
                    else "تم تغذية العقل بـ ${styles.size} نمط | التقدم +0…+3 لكل امتصاص",
                    color = if (styles.isEmpty() && strength == 0) theme.warning else theme.success,
                    fontFamily = TajawalFont,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { strength / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = PresetPrimary,
                    trackColor = DeepSlate
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = coreStyle.analysis,
                    color = TextSecondary,
                    fontFamily = TajawalFont,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎨 سمات بصرية", color = PresetAccent, fontSize = 11.sp, fontFamily = TajawalFont)
                        Text("${coreStyle.visualTraits.size} خصال", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎬 سمات حركة", color = PresetAccent, fontSize = 11.sp, fontFamily = TajawalFont)
                        Text("${coreStyle.motionTraits.size} خصال", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✍️ سمات نصوص", color = PresetAccent, fontSize = 11.sp, fontFamily = TajawalFont)
                        Text("${coreStyle.textTraits.size} خصال", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardSurface,
            contentColor = PresetPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PresetPrimary
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("الأنماط", fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("مقارنة ودمج", fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("التعلم المستمر", fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("الماستر", fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTab) {
            0 -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "استخراج وتخزين نمط فني (Style Object)",
                            color = TextPrimary,
                            fontFamily = CairoFont,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // حالة توفر المفتاح
                        Surface(
                            color = if (isApiKeyAvailable) theme.success.copy(alpha = 0.15f) else theme.destructive.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isApiKeyAvailable) theme.success.copy(alpha = 0.5f) else theme.destructive.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isApiKeyAvailable) Icons.Default.CheckCircle else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isApiKeyAvailable) theme.success else theme.destructive,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    if (isApiKeyAvailable) "مفتاح API جاهز" else "مفتاح API غير متوفر",
                                    color = if (isApiKeyAvailable) theme.success else theme.destructive,
                                    fontFamily = TajawalFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // تنبيه عند عدم توفر مفتاح الذكاء الاصطناعي
                    if (!isApiKeyAvailable) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = theme.destructive.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.destructive.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = theme.destructive,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "مفتاح الذكاء الاصطناعي غير متوفر",
                                        color = theme.destructive,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "يتطلب استخراج الأنماط توفير مفتاح Gemini أو Groq في الإعدادات للرؤية الحاسوبية. تم تجميد زر الاستخراج لحين توفر المفتاح.",
                                        color = TextPrimary.copy(alpha = 0.85f),
                                        fontFamily = TajawalFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.separator),
                        enabled = !absorptionState.isAbsorbing,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (selectedVideoLabel != null) "تغيير الفيديو المرجعي" else "اختيار فيديو من الجهاز",
                            color = PresetPrimary,
                            fontFamily = TajawalFont,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (selectedVideoLabel != null || videoPath.isNotBlank()) {
                        Text(
                            "الملف: ${selectedVideoLabel ?: videoPath.takeLast(40)}",
                            color = theme.success,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedTextField(
                        value = videoPath,
                        onValueChange = {
                            videoPath = it
                            if (it.startsWith("http")) selectedVideoLabel = it.substringAfterLast("/").take(40)
                        },
                        label = { Text("أو الصق رابط فيديو / مساراً يدوياً", fontFamily = TajawalFont) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !absorptionState.isAbsorbing,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PresetPrimary,
                            unfocusedBorderColor = theme.separator,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = styleName,
                        onValueChange = { styleName = it },
                        label = { Text("اسم النمط الفني (مثلاً: أسلوب وثائقي مهيب)", fontFamily = TajawalFont) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !absorptionState.isAbsorbing,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PresetPrimary,
                            unfocusedBorderColor = theme.separator,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // واجهة التحميل وتتبع المراحل الثلاث بدقة (ViewModel-backed Progress Bar)
                    if (absorptionState.isAbsorbing || (absorptionState.stage != StyleAbsorptionStage.IDLE && absorptionState.stage != StyleAbsorptionStage.FAILED)) {
                        StyleAbsorptionProgressCard(state = absorptionState)
                    }

                    // رسالة الخطأ
                    if (absorptionState.errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = theme.destructive.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.destructive.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "تعذر الاستخراج: ${absorptionState.errorMessage}",
                                    color = theme.destructive,
                                    fontFamily = TajawalFont,
                                    fontSize = 11.sp
                                )
                                OutlinedButton(
                                    onClick = { brainViewModel.resetAbsorption() },
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("إخفاء ✕", fontSize = 10.sp, fontFamily = TajawalFont)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = saveToCloud,
                            onCheckedChange = { saveToCloud = it },
                            enabled = !absorptionState.isAbsorbing,
                            colors = CheckboxDefaults.colors(checkedColor = PresetPrimary, checkmarkColor = DeepSlate)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ في سحابة Firestore", color = TextSecondary, fontFamily = TajawalFont, fontSize = 12.sp)
                    }

                    // زر الاستخراج مع تجميده عند عدم توفر API Key
                    val canAbsorb = isApiKeyAvailable && !absorptionState.isAbsorbing && (videoPath.isNotBlank() || selectedVideoLabel != null)

                    Button(
                        onClick = {
                            if (canAbsorb) {
                                message = null
                                brainViewModel.startAbsorption(
                                    videoPath = videoPath,
                                    styleName = styleName,
                                    saveToCloud = saveToCloud
                                ) { newStyle ->
                                    message = "تم استخراج [${newStyle.name}] بنجاح (تقييم ${newStyle.overallScore}%)."
                                    videoPath = ""
                                    styleName = ""
                                    selectedVideoLabel = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isApiKeyAvailable) PresetPrimary else theme.separator,
                            disabledContainerColor = theme.separator,
                            disabledContentColor = theme.textTertiary
                        ),
                        enabled = canAbsorb
                    ) {
                        if (absorptionState.isAbsorbing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepSlate, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "جاري الاستخراج (${absorptionState.overallPercentage}%)...",
                                color = DeepSlate,
                                fontFamily = TajawalFont,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (!isApiKeyAvailable) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "🔒 الزر مجمّد (يتطلب مفتاح ذكاء اصطناعي)",
                                color = theme.textSecondary,
                                fontFamily = TajawalFont,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "استخراج النمط من الفيديو (تحليل آمن)",
                                color = DeepSlate,
                                fontFamily = TajawalFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    message?.let {
                        Text(it, color = PresetPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                }
            }

            Text("الأنماط الفنية المستخرجة (${styles.size})", color = TextPrimary, fontFamily = CairoFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            if (styles.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("لا توجد أنماط مستخرجة بعد", color = PresetPrimary, fontFamily = CairoFont, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "اختر فيديو ثم اضغط استخراج. التقدم لكل امتصاص بين 0 و 3 فقط حسب قوة الأسلوب.",
                            color = TextSecondary,
                            fontFamily = TajawalFont,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                styles.forEach { style ->
                    val isPrimary = primaryStyleId == style.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isPrimary) 1.5.dp else 1.dp,
                            if (isPrimary) PresetPrimary else theme.separator
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(style.name, color = PresetPrimary, fontFamily = TajawalFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        if (isPrimary) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = PresetPrimary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, PresetPrimary)
                                            ) {
                                                Text(
                                                    "الأساسي للاستوديو 🌟",
                                                    color = PresetPrimary,
                                                    fontFamily = TajawalFont,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text("تقييم: ${style.overallScore}%", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        if (isPrimary) {
                                            StyleBrain.setPrimaryStudioStyle(context, null)
                                        } else {
                                            StyleBrain.setPrimaryStudioStyle(context, style.id)
                                        }
                                    }) {
                                        Icon(
                                            if (isPrimary) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = if (isPrimary) "إلغاء الاعتماد كأساسي" else "تعيين كأساسي للاستوديو",
                                            tint = if (isPrimary) PresetPrimary else TextSecondary
                                        )
                                    }
                                    IconButton(onClick = {
                                        if (primaryStyleId == style.id) StyleBrain.setPrimaryStudioStyle(context, null)
                                        StyleBrain.removeStyle(context, style.id)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = theme.destructive.copy(alpha = 0.7f))
                                    }
                                }
                            }
                            Text(style.analysis, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)

                            if (style.visualTraits.isNotEmpty() || style.motionTraits.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    style.visualTraits.take(2).forEach { trait ->
                                        Surface(
                                            color = theme.separator,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                trait.take(25),
                                                color = theme.textSecondary,
                                                fontFamily = TajawalFont,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    style.motionTraits.take(1).forEach { trait ->
                                        Surface(
                                            color = theme.secondarySurface,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                trait.take(25),
                                                color = PresetPrimary.copy(alpha = 0.8f),
                                                fontFamily = TajawalFont,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                maxLines = 1
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
            1 -> {
                Text("مقارنة ودمج الأساليب الفنية", color = TextPrimary, fontFamily = CairoFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (styles.size < 2) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(32.dp))
                            Text("يلزم وجود أسلوبين على الأقل للمقارنة والدمج", color = PresetPrimary, fontFamily = CairoFont, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "قم باستخراج أسلوبين من فيديوهات مختلفة في تبويب «الأنماط» أولاً، لتتمكن من مقارنة سماتهما اللونية والحركية ودمجهما.",
                                color = TextSecondary,
                                fontFamily = TajawalFont,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("1. اختيار النمطين للمقارنة", color = PresetPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("النمط الأول (أ)", color = TextSecondary, fontFamily = TajawalFont, fontSize = 12.sp)
                                    var expandedPrimary by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { expandedPrimary = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (primaryStyleForFusion != null) PresetPrimary else theme.separator),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                primaryStyleForFusion?.name?.take(18) ?: "اختر النمط الأول",
                                                color = if (primaryStyleForFusion != null) PresetPrimary else TextSecondary,
                                                fontFamily = TajawalFont,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        DropdownMenu(expanded = expandedPrimary, onDismissRequest = { expandedPrimary = false }) {
                                            styles.forEach { style ->
                                                DropdownMenuItem(
                                                    text = { 
                                                        Column {
                                                            Text(style.name, color = TextPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
                                                            Text("تقييم: ${style.overallScore}%", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                                                        }
                                                    },
                                                    onClick = { primaryStyleForFusion = style; expandedPrimary = false }
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("النمط الثاني (ب)", color = TextSecondary, fontFamily = TajawalFont, fontSize = 12.sp)
                                    var expandedSecondary by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { expandedSecondary = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (secondaryStyleForFusion != null) theme.primary else theme.separator),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                secondaryStyleForFusion?.name?.take(18) ?: "اختر النمط الثاني",
                                                color = if (secondaryStyleForFusion != null) theme.primary else TextSecondary,
                                                fontFamily = TajawalFont,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        DropdownMenu(expanded = expandedSecondary, onDismissRequest = { expandedSecondary = false }) {
                                            styles.forEach { style ->
                                                DropdownMenuItem(
                                                    text = { 
                                                        Column {
                                                            Text(style.name, color = TextPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
                                                            Text("تقييم: ${style.overallScore}%", color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
                                                        }
                                                    },
                                                    onClick = { secondaryStyleForFusion = style; expandedSecondary = false }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val s1 = primaryStyleForFusion
                            val s2 = secondaryStyleForFusion

                            if (s1 != null && s2 != null && s1.id != s2.id) {
                                HorizontalDivider(color = theme.separator, thickness = 1.dp)

                                Text("2. جدول مقارنة الفروقات الفنية ⚖️", color = PresetPrimary, fontFamily = CairoFont, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                                StyleComparisonCard(style1 = s1, style2 = s2)

                                HorizontalDivider(color = theme.separator, thickness = 1.dp)

                                Text("3. موازنة نسبة الدمج والانصهار", color = PresetPrimary, fontFamily = CairoFont, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                                Slider(
                                    value = blendRatio,
                                    onValueChange = { blendRatio = it },
                                    valueRange = 0.1f..0.9f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PresetPrimary,
                                        activeTrackColor = PresetPrimary,
                                        inactiveTrackColor = theme.primary.copy(alpha = 0.4f)
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "وزن النمط الأول: ${((1f - blendRatio) * 100).toInt()}%",
                                        color = PresetPrimary,
                                        fontFamily = TajawalFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "وزن النمط الثاني: ${(blendRatio * 100).toInt()}%",
                                        color = theme.primary,
                                        fontFamily = TajawalFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = theme.secondarySurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("معاينة النمط الهجين المتوقع:", color = PresetAccent, fontFamily = TajawalFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        val expectedPrimaryColor = if ((1f - blendRatio) >= 0.5f) s1.resolvePrimaryColorHex() else s2.resolvePrimaryColorHex()
                                        val expectedFilter = if ((1f - blendRatio) >= 0.5f) s1.resolveFilterHint() else s2.resolveFilterHint()
                                        val expectedMotion = if ((1f - blendRatio) >= 0.5f) s1.resolveMotionType() else s2.resolveMotionType()
                                        val expectedTempo = if ((1f - blendRatio) >= 0.5f) s1.resolveTempo() else s2.resolveTempo()
                                        val expectedScore = (((s1.overallScore * (1f - blendRatio)) + (s2.overallScore * blendRatio))).toInt().coerceIn(80, 99)

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(parseHexColor(expectedPrimaryColor), RoundedCornerShape(3.dp))
                                            )
                                            Text(
                                                "لون: $expectedPrimaryColor | فلتر: $expectedFilter | حركة: $expectedMotion | إيقاع: $expectedTempo | جودة: $expectedScore%",
                                                color = TextPrimary,
                                                fontFamily = NotoSansFont,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = customFusedName,
                                    onValueChange = { customFusedName = it },
                                    label = { Text("اسم النمط المدموج (افتراضي: دمج ${s1.name.take(8)} + ${s2.name.take(8)})", fontFamily = TajawalFont) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PresetPrimary,
                                        unfocusedBorderColor = theme.separator,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                StyleBrain.fuseAbsorbedStyles(
                                                    context = context,
                                                    primary = s1,
                                                    secondary = s2,
                                                    blendRatio = blendRatio,
                                                    customName = customFusedName
                                                )
                                                message = "تم الدمج بنجاح وإنشاء أسلوب جديد في الذاكرة التراكمية!"
                                                Toast.makeText(context, "تم دمج الأساليب وحفظ الأسلوب الجديد ✓", Toast.LENGTH_SHORT).show()
                                                selectedTab = 0
                                                primaryStyleForFusion = null
                                                secondaryStyleForFusion = null
                                                customFusedName = ""
                                                blendRatio = 0.5f
                                            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                                message = "فشل الدمج: ${e.message}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("تنفيذ وحفظ الأسلوب المدموج", color = DeepSlate, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
                                }
                            } else if (s1 != null && s2 != null && s1.id == s2.id) {
                                Text("⚠️ لا يمكن دمج النمط مع نفسه. يرجى اختيار نمط ثانٍ مختلف لمقارنة الفروقات ودمجها.", color = theme.destructive, fontFamily = TajawalFont, fontSize = 12.sp)
                            } else {
                                Text("👆 اختر نمطين أعلاه لتفعيل جدول المقارنة والدمج المباشر.", color = TextSecondary, fontFamily = TajawalFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            2 -> {
                var hasCompletedProjects by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    try {
                        val projects = ProjectService(context).getLocalProjects()
                        hasCompletedProjects = projects.any {
                            it.finalVideoPath.isNotBlank() ||
                            it.status.equals("COMPLETED", ignoreCase = true) ||
                            it.status.contains("ناجح") ||
                            it.status.contains("تم")
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        hasCompletedProjects = false
                    }
                }
                
                ContinuousLearningTab(
                    context = context,
                    isEnabled = isContinuousLearningEnabled,
                    proposals = improvementProposals,
                    lastTimestamp = lastLearningTimestamp,
                    isAnalyzing = isAnalyzingLearning,
                    activePrimaryStyle = styles.find { it.id == primaryStyleId },
                    coreStyle = coreStyle,
                    hasData = styles.isNotEmpty() && hasCompletedProjects
                )
            }
            3 -> {
                Text("ملفات الماستر (${masterStyles.size})", color = TextPrimary, fontFamily = CairoFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (masterStyles.isEmpty()) {
                Text(
                    "لا يوجد ملف ماستر بعد.",
                    color = TextSecondary,
                    fontFamily = TajawalFont,
                    fontSize = 13.sp
                )
            }
            masterStyles.forEach { master ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(master.name, color = PresetPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
                        Text(master.description.ifBlank { master.fusionSummary }, color = TextSecondary, fontSize = 12.sp, fontFamily = NotoSansFont)
                    }
                }
            }
        }
        } // End of when
    }

    if (showHardResetConfirm) {
        AlertDialog(
            onDismissRequest = { showHardResetConfirm = false },
            title = {
                Text("تصفير العقل نهائياً؟", color = theme.destructive, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "سيتم مسح كل الأنماط الممتصة وإعادة قوة العقل إلى 0. التقدم لاحقاً سيكون بحد أقصى +3 لكل فيديو حسب قوة الأسلوب.",
                    color = TextSecondary,
                    fontFamily = TajawalFont,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        StyleBrainLoadFix.hardResetBrain(context)
                        showHardResetConfirm = false
                        message = "تم تصفير العقل: قوة = 0 | أنماط = 0"
                        Toast.makeText(context, "تم تصفير العقل نهائياً ✓", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.destructive)
                ) {
                    Text("تصفير الآن", color = theme.onPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHardResetConfirm = false }) {
                    Text("إلغاء", color = theme.textTertiary, fontFamily = TajawalFont)
                }
            },
            containerColor = CardSurface
        )
    }
}

@Composable
fun StyleComparisonCard(style1: AbsorbedStyle, style2: AbsorbedStyle) {
    val theme = QabasTheme.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.secondarySurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = CardSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("النمط الأول (أ)", color = PresetAccent, fontFamily = TajawalFont, fontSize = 10.sp)
                        Text(style1.name, color = PresetPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        Text("تقييم: ${style1.overallScore}%", color = TextSecondary, fontFamily = CairoFont, fontSize = 10.sp)
                    }
                }

                Text(" ⚔️ ", color = PresetAccent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))

                Surface(
                    modifier = Modifier.weight(1f),
                    color = CardSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("النمط الثاني (ب)", color = theme.primary, fontFamily = TajawalFont, fontSize = 10.sp)
                        Text(style2.name, color = theme.primary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        Text("تقييم: ${style2.overallScore}%", color = TextSecondary, fontFamily = CairoFont, fontSize = 10.sp)
                    }
                }
            }

            // Radar Chart Section
            StyleRadarChart(
                style1 = style1,
                style2 = style2,
                color1 = PresetPrimary,
                color2 = theme.primary
            )

            HorizontalDivider(color = theme.separator, thickness = 1.dp)

            // 1. الألوان
            val s1Primary = style1.resolvePrimaryColorHex()
            val s1Bg = style1.resolveBackgroundColorHex()
            val s1Accent = style1.resolveAccentColorHex()
            val s2Primary = style2.resolvePrimaryColorHex()
            val s2Bg = style2.resolveBackgroundColorHex()
            val s2Accent = style2.resolveAccentColorHex()

            ComparisonRow(
                traitTitle = "الألوان الرئيسية والإضاءة",
                value1 = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        ColorSwatch(parseHexColor(s1Primary), s1Primary)
                        ColorSwatch(parseHexColor(s1Accent), s1Accent)
                        ColorSwatch(parseHexColor(s1Bg), s1Bg)
                    }
                },
                value2 = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        ColorSwatch(parseHexColor(s2Primary), s2Primary)
                        ColorSwatch(parseHexColor(s2Accent), s2Accent)
                        ColorSwatch(parseHexColor(s2Bg), s2Bg)
                    }
                },
                isDifferent = (s1Primary != s2Primary || s1Bg != s2Bg || s1Accent != s2Accent)
            )

            // 2. الفلتر والصبغة
            val s1Filter = style1.resolveFilterHint()
            val s2Filter = style2.resolveFilterHint()
            ComparisonRow(
                traitTitle = "الفلتر والصبغة البصرية",
                value1 = { TraitBadge(getFilterArabicLabel(s1Filter), PresetPrimary) },
                value2 = { TraitBadge(getFilterArabicLabel(s2Filter), theme.primary) },
                isDifferent = (s1Filter != s2Filter)
            )

            // 3. حركة الكاميرا
            val s1Motion = style1.resolveMotionType()
            val s2Motion = style2.resolveMotionType()
            ComparisonRow(
                traitTitle = "حركة الكاميرا والمشهد",
                value1 = { TraitBadge(getMotionArabicLabel(s1Motion), PresetPrimary) },
                value2 = { TraitBadge(getMotionArabicLabel(s2Motion), theme.primary) },
                isDifferent = (s1Motion != s2Motion)
            )

            // 4. نوع الانتقالات
            val s1Transition = style1.resolveTransitionType()
            val s2Transition = style2.resolveTransitionType()
            ComparisonRow(
                traitTitle = "الانتقالات السينمائية",
                value1 = { TraitBadge(getTransitionArabicLabel(s1Transition), PresetPrimary) },
                value2 = { TraitBadge(getTransitionArabicLabel(s2Transition), theme.primary) },
                isDifferent = (s1Transition != s2Transition)
            )

            // 5. إيقاع وسرعة المونتاج
            val s1Tempo = style1.resolveTempo()
            val s2Tempo = style2.resolveTempo()
            ComparisonRow(
                traitTitle = "إيقاع وسرعة الفيديو",
                value1 = { TraitBadge(getTempoArabicLabel(s1Tempo), PresetPrimary) },
                value2 = { TraitBadge(getTempoArabicLabel(s2Tempo), theme.primary) },
                isDifferent = (s1Tempo != s2Tempo)
            )

            // 6. نمط الكابشن
            val s1Caption = style1.resolveTextAnimation()
            val s2Caption = style2.resolveTextAnimation()
            ComparisonRow(
                traitTitle = "حركة ظهور النصوص",
                value1 = { TraitBadge(getTextAnimArabicLabel(s1Caption), PresetPrimary) },
                value2 = { TraitBadge(getTextAnimArabicLabel(s2Caption), theme.primary) },
                isDifferent = (s1Caption != s2Caption)
            )
        }
    }
}

@Composable
fun ComparisonRow(
    traitTitle: String,
    value1: @Composable () -> Unit,
    value2: @Composable () -> Unit,
    isDifferent: Boolean
) {
    val theme = QabasTheme.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(traitTitle, color = TextSecondary, fontFamily = TajawalFont, fontSize = 11.sp)
            if (isDifferent) {
                Text("مختلف ⚡", color = theme.warning, fontFamily = TajawalFont, fontSize = 10.sp)
            } else {
                Text("متطابق ✓", color = theme.success, fontFamily = TajawalFont, fontSize = 10.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                value1()
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                value2()
            }
        }
        HorizontalDivider(color = theme.separator.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}

@Composable
fun ColorSwatch(color: Color, tooltip: String) {
    val theme = QabasTheme.current
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(color, RoundedCornerShape(4.dp))
            .border(0.5.dp, theme.textPrimary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
    )
}

@Composable
fun TraitBadge(text: String, accentColor: Color) {
    Surface(
        color = accentColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            color = accentColor,
            fontFamily = TajawalFont,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

fun parseHexColor(hex: String, fallback: Color = PresetPrimary): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        if (clean.length == 6) {
            Color(android.graphics.Color.parseColor("#$clean"))
        } else if (clean.length == 8) {
            Color(android.graphics.Color.parseColor("#$clean"))
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

fun getFilterArabicLabel(filter: String): String = when (filter.lowercase()) {
    "warm_gold" -> "ذهبي دافئ"
    "cool_emerald" -> "زمردي عميق"
    "soft_desert" -> "صحراوي ناعم"
    "high_contrast_dark" -> "تباين داكن حاد"
    "ai_violet" -> "بنفسجي تقني"
    else -> filter
}

fun getMotionArabicLabel(motion: String): String = when (motion.lowercase()) {
    "slow_zoom", "slow_zoom_in" -> "تقريب بطيء (Zoom)"
    "slow_zoom_out" -> "تبعيد ناعم"
    "pan_horizontal", "pan" -> "تحريك أفقي (Pan)"
    "static", "ثابت" -> "ثابت وقور"
    else -> motion
}

fun getTransitionArabicLabel(trans: String): String = when (trans.lowercase()) {
    "dissolve", "تلاشي" -> "تلاشي متداخل (Dissolve)"
    "fade_black", "fade" -> "تلاشي للظلمة"
    "cut", "قطع" -> "قطع سينمائي مباشر"
    "slide" -> "انزلاق جانبي"
    else -> trans
}

fun getTempoArabicLabel(tempo: String): String = when (tempo.lowercase()) {
    "خاشع", "slow" -> "خاشع ومتأنٍ 🕊️"
    "متوسط", "medium" -> "متوازن ومنتظم ⚖️"
    "سريع", "fast" -> "حماسي وخاطف ⚡"
    else -> tempo
}

fun getTextAnimArabicLabel(anim: String): String = when (anim.lowercase()) {
    "wordbyword", "word_by_word" -> "كلمة بكلمة متتابعة"
    "fade" -> "تلاشي تدريجي"
    "kinetic" -> "حركي ديناميكي"
    else -> anim
}

@Composable
fun StyleRadarChart(
    style1: AbsorbedStyle,
    style2: AbsorbedStyle,
    color1: Color = PresetPrimary,
    color2: Color = QabasTheme.current.primary,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val axisLabels = listOf(
        "عمق الألوان",
        "التباين والإضاءة",
        "الإيقاع والسرعة",
        "ديناميكية الحركة",
        "جرأة الخط والطباعة"
    )

    val scores1 = remember(style1) { evaluateStyleRadarScores(style1) }
    val scores2 = remember(style2) { evaluateStyleRadarScores(style2) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.background,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📊 الرسم البياني الراداري لتوزيع السمات",
                    color = PresetPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    "مطور الاستوديو 🔒",
                    color = TextSecondary,
                    fontFamily = TajawalFont,
                    fontSize = 10.sp
                )
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color1, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(style1.name.take(12), color = color1, fontFamily = TajawalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color2, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(style2.name.take(12), color = color2, fontFamily = TajawalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Canvas Radar Chart
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .padding(8.dp)
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.minDimension / 2f) - 34.dp.toPx()
                val numAxes = axisLabels.size
                val angleStep = (2 * Math.PI / numAxes).toFloat()
                val startAngle = (-Math.PI / 2).toFloat() // Start at top

                // 1. Draw web grid levels (20%, 40%, 60%, 80%, 100%)
                val levels = 5
                for (level in 1..levels) {
                    val currentRadius = radius * (level.toFloat() / levels)
                    val gridPath = Path()
                    for (i in 0 until numAxes) {
                        val angle = startAngle + i * angleStep
                        val x = center.x + currentRadius * cos(angle)
                        val y = center.y + currentRadius * sin(angle)
                        if (i == 0) gridPath.moveTo(x, y) else gridPath.lineTo(x, y)
                    }
                    gridPath.close()
                    drawPath(
                        path = gridPath,
                        color = theme.separator,
                        style = Stroke(width = if (level == levels) 1.5f else 1f)
                    )
                }

                // 2. Draw axis lines & labels
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#94A3B8")
                    textSize = 9.sp.toPx()
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                for (i in 0 until numAxes) {
                    val angle = startAngle + i * angleStep
                    val endX = center.x + radius * cos(angle)
                    val endY = center.y + radius * sin(angle)

                    drawLine(
                        color = theme.separator,
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 1f
                    )

                    // Draw label text
                    val labelRadius = radius + 20.dp.toPx()
                    val labelX = center.x + labelRadius * cos(angle)
                    val labelY = center.y + labelRadius * sin(angle) + (if (sin(angle) > 0.3) 10f else if (sin(angle) < -0.3) -2f else 4f)

                    drawContext.canvas.nativeCanvas.drawText(
                        axisLabels[i],
                        labelX,
                        labelY,
                        textPaint
                    )
                }

                // 3. Draw polygon for Style 1
                val path1 = Path()
                val points1 = mutableListOf<Offset>()
                for (i in 0 until numAxes) {
                    val angle = startAngle + i * angleStep
                    val score = (scores1.getOrElse(i) { 50 } / 100f).coerceIn(0.1f, 1.0f)
                    val r = radius * score
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    val pt = Offset(x, y)
                    points1.add(pt)
                    if (i == 0) path1.moveTo(x, y) else path1.lineTo(x, y)
                }
                path1.close()

                drawPath(path1, color = color1.copy(alpha = 0.25f), style = Fill)
                drawPath(path1, color = color1, style = Stroke(width = 2.dp.toPx()))
                points1.forEach { pt ->
                    drawCircle(color = color1, radius = 3.5.dp.toPx(), center = pt)
                }

                // 4. Draw polygon for Style 2
                val path2 = Path()
                val points2 = mutableListOf<Offset>()
                for (i in 0 until numAxes) {
                    val angle = startAngle + i * angleStep
                    val score = (scores2.getOrElse(i) { 50 } / 100f).coerceIn(0.1f, 1.0f)
                    val r = radius * score
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    val pt = Offset(x, y)
                    points2.add(pt)
                    if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
                }
                path2.close()

                drawPath(path2, color = color2.copy(alpha = 0.22f), style = Fill)
                drawPath(path2, color = color2, style = Stroke(width = 2.dp.toPx()))
                points2.forEach { pt ->
                    drawCircle(color = color2, radius = 3.5.dp.toPx(), center = pt)
                }
            }

            // Summary breakdown badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val s1Avg = scores1.average().toInt()
                val s2Avg = scores2.average().toInt()
                Text("متوسط قوة سمات (أ): $s1Avg%", color = color1, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("متوسط قوة سمات (ب): $s2Avg%", color = color2, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Calculates normalized 0-100 radar axis scores based on absorbed style traits and directives:
 * 1. عمق الألوان (Color Depth & Saturation)
 * 2. التباين والإضاءة (Contrast & Lighting)
 * 3. الإيقاع والسرعة (Rhythm & Tempo)
 * 4. ديناميكية الحركة (Camera & Motion Dynamics)
 * 5. جرأة الخط والطباعة (Typography & Caption Weight)
 */
fun evaluateStyleRadarScores(style: AbsorbedStyle): List<Int> {
    val blob = (style.name + " " + style.analysis + " " +
            style.visualTraits.joinToString(" ") + " " +
            style.motionTraits.joinToString(" ") + " " +
            style.textTraits.joinToString(" ")).lowercase()

    // 1. عمق الألوان
    var colorDepth = 65
    if (blob.contains("ذهب") || blob.contains("gold") || blob.contains("زمرد") || blob.contains("emerald")) colorDepth += 20
    if (blob.contains("ألوان") || blob.contains("مشبع") || blob.contains("عميق") || blob.contains("داكن")) colorDepth += 10
    if (style.resolvePrimaryColorHex().isNotBlank()) colorDepth += 5

    // 2. التباين والإضاءة
    var contrast = 60
    val filter = style.resolveFilterHint().lowercase()
    if (filter.contains("high_contrast") || blob.contains("تباين") || blob.contains("contrast")) contrast += 30
    if (blob.contains("ظلال") || blob.contains("إضاءة") || blob.contains("توهج") || blob.contains("shadow")) contrast += 15
    if (filter.contains("warm") || filter.contains("desert")) contrast += 10

    // 3. الإيقاع والسرعة
    var tempo = 50
    val tempoStr = style.resolveTempo().lowercase()
    if (tempoStr.contains("سريع") || tempoStr.contains("fast") || blob.contains("خاطف") || blob.contains("حماسي")) tempo = 90
    else if (tempoStr.contains("متوسط") || tempoStr.contains("medium") || blob.contains("متوازن")) tempo = 65
    else if (tempoStr.contains("خاشع") || tempoStr.contains("slow") || blob.contains("بطيء") || blob.contains("متأن")) tempo = 35

    // 4. ديناميكية الحركة
    var motion = 55
    val motionStr = style.resolveMotionType().lowercase()
    val transStr = style.resolveTransitionType().lowercase()
    if (motionStr.contains("pan") || blob.contains("تحريك") || blob.contains("حركي")) motion += 25
    if (motionStr.contains("zoom") || blob.contains("زووم") || blob.contains("تقريب")) motion += 20
    if (transStr.contains("dissolve") || transStr.contains("slide") || transStr.contains("cut")) motion += 10
    if (motionStr.contains("static") || blob.contains("ثابت")) motion = (motion - 20).coerceAtLeast(30)

    // 5. جرأة الخط والطباعة
    var typography = 60
    val captionAnim = style.resolveTextAnimation().lowercase()
    if (captionAnim.contains("wordbyword") || blob.contains("كلمة") || blob.contains("kinetic")) typography += 30
    if (blob.contains("عريض") || blob.contains("bold") || blob.contains("خط")) typography += 15
    if (style.textTraits.isNotEmpty()) typography += 10

    return listOf(
        colorDepth.coerceIn(20, 100),
        contrast.coerceIn(20, 100),
        tempo.coerceIn(20, 100),
        motion.coerceIn(20, 100),
        typography.coerceIn(20, 100)
    )
}

@Composable
fun ContinuousLearningTab(
    context: Context,
    isEnabled: Boolean,
    proposals: List<StyleImprovementProposal>,
    lastTimestamp: Long,
    isAnalyzing: Boolean,
    activePrimaryStyle: AbsorbedStyle?,
    coreStyle: QabasCoreStyle,
    hasData: Boolean
) {
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    var actionToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(actionToast) {
        actionToast?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            actionToast = null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. كارت رأس المحرك والتحكم الرئيسي
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(24.dp))
                        Text(
                            "محرك التعلم المستمر 🧠",
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { enabled ->
                            StyleBrain.setContinuousLearningEnabled(context, enabled)
                            actionToast = if (enabled) "تم تفعيل التعلم المستمر التلقائي ✓" else "تم إيقاف التعلم المستمر مؤقتاً"
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepSlate,
                            checkedTrackColor = PresetPrimary,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = theme.separator
                        )
                    )
                }

                Text(
                    "يقوم النظام بتحليل فيديوهات وإنتاجات الاستوديو السابقة تلقائياً لاستخلاص مقاييس النجاح البصري، واقتراح تحسينات دقيقة لتطوير الأسلوب النشط باستمرار.",
                    color = TextSecondary,
                    fontFamily = TajawalFont,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Divider(color = theme.separator)

                // معلومات آخر تحليل وزر الفحص الفوري
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "آخر فحص لفيديوهات الاستوديو:",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = TajawalFont
                        )
                        Text(
                            if (lastTimestamp > 0L) {
                                val diffMin = ((System.currentTimeMillis() - lastTimestamp) / (1000 * 60)).coerceAtLeast(0)
                                if (diffMin < 2) "منذ لحظات" else "قبل $diffMin دقيقة"
                            } else "لم يتم إجراء تحليل بعد",
                            color = if (lastTimestamp > 0L) theme.success else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NotoSansFont
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val results = StyleBrain.runContinuousLearningAnalysis(context, force = true)
                                    actionToast = "تم فحص الفيديوهات وتوليد ${results.size} مقترحات فنية! ✨"
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                    actionToast = "فشل التحليل: ${e.message}"
                                }
                            }
                        },
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جاري الفحص...", color = DeepSlate, fontFamily = TajawalFont, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فحص الإنتاجات الآن 🔄", color = DeepSlate, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. كارت الأسلوب المستهدف بالتطوير
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = theme.cardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🎯 الأسلوب المستهدف للترقية:", color = PresetAccent, fontSize = 12.sp, fontFamily = TajawalFont)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PresetPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                if (activePrimaryStyle != null) "أساسي للاستوديو 🌟" else "العقل التراكمي المباشر",
                                color = PresetPrimary,
                                fontSize = 10.sp,
                                fontFamily = TajawalFont,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        activePrimaryStyle?.name ?: "هوية خادم المسلم الأساسية",
                        color = TextPrimary,
                        fontFamily = CairoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = theme.separator,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("القوة الحالية", color = TextSecondary, fontSize = 10.sp, fontFamily = TajawalFont)
                        Text(
                            "${activePrimaryStyle?.overallScore ?: coreStyle.strengthScore}%",
                            color = PresetPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NotoSansFont
                        )
                    }
                }
            }
        }

        // 3. قائمة التوصيات والمقترحات
        Text(
            "💡 التوصيات ومقترحات التطوير (${proposals.size})",
            color = TextPrimary,
            fontFamily = CairoFont,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        if (!hasData) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                    Text(
                        "بانتظار البيانات",
                        color = TextPrimary,
                        fontFamily = CairoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "محرك التعلم المستمر بحاجة إلى استخراج أساليب أو إنتاج فيديوهات جديدة ليتمكن من استخلاص مقترحات تطوير حقيقية.",
                        color = TextSecondary,
                        fontFamily = TajawalFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (proposals.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                    Text(
                        "لا توجد مقترحات معلقة حالياً",
                        color = TextPrimary,
                        fontFamily = CairoFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "اضغط على زر «فحص الإنتاجات الآن 🔄» أعلاه لتحليل الفيديوهات المكتملة في الاستوديو واستخراج توصيات لتطوير الأسلوب.",
                        color = TextSecondary,
                        fontFamily = TajawalFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            proposals.forEach { proposal ->
                ProposalCard(
                    proposal = proposal,
                    onApply = {
                        val ok = StyleBrain.applyImprovementProposal(context, proposal.id)
                        if (ok) actionToast = "تم تطبيق التحسين وترقية الأسلوب بنجاح! 🚀"
                    },
                    onDismiss = {
                        StyleBrain.dismissImprovementProposal(context, proposal.id)
                        actionToast = "تم تجاهل المقترح"
                    }
                )
            }
        }
    }
}

@Composable
fun ProposalCard(
    proposal: StyleImprovementProposal,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = QabasTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // شريط الفئة والنسبة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = theme.separator
                ) {
                    Text(
                        "🏷️ " + proposal.targetTraitCategory,
                        color = PresetAccent,
                        fontSize = 11.sp,
                        fontFamily = TajawalFont,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = theme.success.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "نجاح متوقع ${proposal.successRateBasedOn}% 📈",
                            color = theme.success,
                            fontSize = 11.sp,
                            fontFamily = TajawalFont,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PresetPrimary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "+${proposal.enhancementScore} نقاط",
                            color = PresetPrimary,
                            fontSize = 11.sp,
                            fontFamily = TajawalFont,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // العنوان والوصف
            Text(
                proposal.title,
                color = PresetPrimary,
                fontFamily = CairoFont,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                proposal.description,
                color = TextPrimary,
                fontFamily = TajawalFont,
                fontSize = 13.sp
            )

            // الأساس التحليلي
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.secondarySurface, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "🔍 الأساس التحليلي من إنتاجاتك السابقة:",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TajawalFont
                    )
                    Text(
                        proposal.rationale,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = TajawalFont,
                        lineHeight = 16.sp
                    )
                }
            }

            // التعديل المقترح
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.separator, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "⚙️ التعديل الفني المقترح:",
                        color = PresetAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TajawalFont
                    )
                    Text(
                        proposal.suggestedChange,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = TajawalFont
                    )
                }
            }

            // أزرار الإجراء
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تطبيق التحسين فوراً 🚀", color = DeepSlate, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.separator),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("تجاهل ✕", color = TextSecondary, fontFamily = TajawalFont, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * بطاقة تتبع مراحل استخراج وامتصاص النمط الفني مع نسب مئوية دقيقة لكل مرحلة
 */
@Composable
fun StyleAbsorptionProgressCard(
    state: StyleAbsorptionUiState,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.secondarySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // شريط العنوان مع النسبة المئوية الكلية
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = PresetPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "مراحل استخراج النمط الفني",
                        color = TextPrimary,
                        fontFamily = CairoFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = PresetPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.6f))
                ) {
                    Text(
                        "${state.overallPercentage}%",
                        color = PresetPrimary,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // شريط التقدم الكلي LinearProgressIndicator
            LinearProgressIndicator(
                progress = { state.overallProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = PresetPrimary,
                trackColor = DeepSlate
            )

            // بطاقات المراحل الثلاث
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // المرحلة الأولى: استخراج الإطارات (0% - 33%)
                StageItem(
                    title = "1. الإطارات",
                    percentage = when {
                        state.overallPercentage >= 33 -> 100
                        state.stage == StyleAbsorptionStage.FRAME_EXTRACTION -> state.stagePercentage
                        else -> 0
                    },
                    isActive = state.stage == StyleAbsorptionStage.FRAME_EXTRACTION,
                    isDone = state.overallPercentage >= 33,
                    modifier = Modifier.weight(1f)
                )

                // المرحلة الثانية: تحليل الألوان والتباين (34% - 66%)
                StageItem(
                    title = "2. الألوان",
                    percentage = when {
                        state.overallPercentage >= 66 -> 100
                        state.stage == StyleAbsorptionStage.COLOR_ANALYSIS -> state.stagePercentage
                        else -> 0
                    },
                    isActive = state.stage == StyleAbsorptionStage.COLOR_ANALYSIS,
                    isDone = state.overallPercentage >= 66,
                    modifier = Modifier.weight(1f)
                )

                // المرحلة الثالثة: استعلام الذكاء الاصطناعي (67% - 100%)
                StageItem(
                    title = "3. الذكاء",
                    percentage = when {
                        state.stage == StyleAbsorptionStage.COMPLETED -> 100
                        state.stage == StyleAbsorptionStage.AI_QUERY || state.stage == StyleAbsorptionStage.SAVING_AND_EVOLVING -> state.stagePercentage
                        else -> 0
                    },
                    isActive = state.stage == StyleAbsorptionStage.AI_QUERY || state.stage == StyleAbsorptionStage.SAVING_AND_EVOLVING,
                    isDone = state.stage == StyleAbsorptionStage.COMPLETED,
                    modifier = Modifier.weight(1f)
                )
            }

            // وصف المرحلة الحالية باللغة العربية
            if (state.statusMessage.isNotBlank()) {
                Surface(
                    color = DeepSlate.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isAbsorbing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = PresetPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            state.statusMessage,
                            color = PresetAccent,
                            fontFamily = TajawalFont,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

/**
 * عنصر مرحلة فردية ضمن شريط التقدم
 */
@Composable
fun StageItem(
    title: String,
    percentage: Int,
    isActive: Boolean,
    isDone: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = QabasTheme.current
    val borderColor = when {
        isDone -> theme.success
        isActive -> PresetPrimary
        else -> theme.separator
    }
    val bgColor = when {
        isDone -> theme.success.copy(alpha = 0.3f)
        isActive -> PresetPrimary.copy(alpha = 0.12f)
        else -> theme.separator.copy(alpha = 0.3f)
    }
    val textColor = when {
        isDone -> theme.success
        isActive -> PresetPrimary
        else -> TextSecondary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                color = textColor,
                fontFamily = TajawalFont,
                fontSize = 10.sp,
                fontWeight = if (isActive || isDone) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Text(
                if (isDone) "100% ✓" else "$percentage%",
                color = if (isDone) theme.success else if (isActive) PresetPrimary else TextSecondary.copy(alpha = 0.7f),
                fontFamily = CairoFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
