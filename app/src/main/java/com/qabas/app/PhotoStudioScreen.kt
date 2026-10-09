package com.qabas.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class PhotoTemplate(
    val title: String,
    val description: String,
    val suggestedText: String,
    val suggestedAudio: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoStudioScreen(
    onBack: () -> Unit,
    onNavigateToAdvancedEdit: (String) -> Unit = {},
    onSaveProject: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(1) } // 1 = Select, 2 = Edit, 3 = Export

    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedTemplate by remember { mutableStateOf<PhotoTemplate?>(null) }

    // عدة التصميم
    var textBlocks by remember { mutableStateOf<List<DesignTextBlock>>(emptyList()) }
    var selectedBlockId by remember { mutableStateOf<Int?>(null) }
    var backgroundMode by remember { mutableStateOf("PHOTO") }
    var bgPresetId by remember { mutableStateOf("SOLID_BLACK") }
    var aiBgPath by remember { mutableStateOf<String?>(null) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var filterId by remember { mutableStateOf("original") }
    var ornamentId by remember { mutableStateOf("none") }
    var showWatermark by remember { mutableStateOf(true) }
    var outputFormat by remember { mutableStateOf("JPEG") }
    var outputQuality by remember { mutableStateOf("HD") }

    var selectedAudioOption by remember { mutableStateOf("تلاوة هادئة") }

    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var selectedCropRatio by remember { mutableStateOf("9:16") }

    var processing by remember { mutableStateOf(false) }
    var generatedVideoPath by remember { mutableStateOf<String?>(null) }
    var exportedPhotoPath by remember { mutableStateOf<String?>(null) }
    var exportType by remember { mutableStateOf("PHOTO") }
    var progressMessage by remember { mutableStateOf("") }

    // أبعاد منطقة المعاينة للجرّ (بالـ px)
    var previewPxW by remember { mutableIntStateOf(0) }
    var previewPxH by remember { mutableIntStateOf(0) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris = (selectedImageUris + uris).distinct()
            if (currentStep == 1 && selectedImageUris.isNotEmpty()) {
                currentStep = 2
            }
        }
    }

    val audioOptions = listOf("بدون صوت", "تلاوة هادئة", "مؤثرات طبيعية", "أنشودة تأملية")

    // قوالب المحتوى الدعوي (كلها نصوص منشورة موثوقة — لا عبارات مخترعة)
    val photoTemplates = remember { listOf(
        PhotoTemplate("قرآن", "آية هادئة", "وَمَا تَوْفِيقِي إِلَّا بِاللَّهِ", "تلاوة هادئة", Icons.Default.Star, GoldPrimary),
        PhotoTemplate("أذكار", "راحة وطمأنينة", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "مؤثرات طبيعية", Icons.Default.Favorite, Color(0xFF6366F1)),
        PhotoTemplate("حديث", "بصمة نبوية", "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ", "بدون صوت", Icons.Default.HistoryEdu, Color(0xFFF5AC37)),
        PhotoTemplate("حكم", "مواعظ مؤثرة", "مَن عَرَفَ نَفْسَهُ اشْتَغَلَ بِإِصْلَاحِهَا", "بدون صوت", Icons.Default.FormatQuote, Color(0xFF10B981)),
        PhotoTemplate("قصة", "سرد متسلسل", "فِي كُلِّ تَأْخِيرَةٍ خِيرَةٌ مِنَ اللَّهِ", "تلاوة هادئة", Icons.Default.List, Color(0xFF0EA5E9)),
        PhotoTemplate("رمضان", "شهر الخير", "اللَّهُمَّ بَلِّغْنَا رَمَضَانَ", "أنشودة تأملية", Icons.Default.StarBorder, Color(0xFFF59E0B)),
        PhotoTemplate("عِيد", "فرحة العيد", "تَقَبَّلَ اللهُ مِنَّا وَمِنْكُمْ", "بدون صوت", Icons.Default.Celebration, Color(0xFF22C55E)),
        PhotoTemplate("تسبيح", "ذِكر دائم", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "مؤثرات طبيعية", Icons.Default.FavoriteBorder, Color(0xFF10B981)),
        PhotoTemplate("حر", "إبداع خاص", "", "بدون صوت", Icons.Default.Edit, Color(0xFF8B5CF6))
    ) }

    val currentBgPreset = remember(backgroundMode, bgPresetId) {
        when (backgroundMode) {
            "SOLID" -> DesignSolidPresets.firstOrNull { it.id == bgPresetId } ?: DesignSolidPresets[0]
            "GRADIENT" -> DesignGradientPresets.firstOrNull { it.id == bgPresetId } ?: DesignGradientPresets[0]
            else -> null
        }
    }

    fun updateBlock(id: Int, transform: (DesignTextBlock) -> DesignTextBlock) {
        textBlocks = textBlocks.map { if (it.id == id) transform(it) else it }
    }

    Column(modifier = Modifier.fillMaxSize().background(DeepSlate)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("استوديو الصور 📸", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    when (currentStep) {
                        1 -> "الخطوة 1: اختيار الصورة أو التصميم الفوري"
                        2 -> "الخطوة 2: المحرر الفاخر"
                        else -> "الخطوة 3: التصدير والمشاركة"
                    },
                    color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp
                )
            }
        }

        when (currentStep) {
            1 -> Step1ImageSelection(
                selectedImageUris = selectedImageUris,
                onSelectMore = { photoPickerLauncher.launch("image/*") },
                onRemoveImage = { uri -> selectedImageUris = selectedImageUris.filter { it != uri } },
                onNext = {
                    if (selectedImageUris.isNotEmpty()) {
                        currentStep = 2
                    } else {
                        Toast.makeText(context, "اختر صورة أولاً أو استخدم التصميم الفوري بدون صور", Toast.LENGTH_SHORT).show()
                    }
                },
                onNextNoPhoto = {
                    backgroundMode = "GRADIENT"
                    bgPresetId = DesignGradientPresets[0].id
                    currentStep = 2
                }
            )
            2 -> Step2Editor(
                selectedImageUris = selectedImageUris,
                textBlocks = textBlocks,
                selectedBlockId = selectedBlockId,
                onSelectBlock = { selectedBlockId = it },
                onAddBlock = {
                    val nextId = (textBlocks.maxOfOrNull { it.id } ?: 0) + 1
                    val count = textBlocks.size
                    textBlocks = textBlocks + DesignTextBlock(
                        id = nextId,
                        style = DesignTextStyle(sizeSp = 26f),
                        posY = (0.35f + count * 0.2f).coerceAtMost(0.8f)
                    )
                    selectedBlockId = nextId
                },
                onRemoveBlock = { id ->
                    textBlocks = textBlocks.filter { it.id != id }
                    if (selectedBlockId == id) selectedBlockId = textBlocks.firstOrNull()?.id
                },
                onUpdateBlock = { id, transform -> updateBlock(id, transform) },
                selectedTemplate = selectedTemplate,
                onTemplateChange = { template ->
                    selectedTemplate = template
                    val firstId = textBlocks.firstOrNull()?.id ?: 1
                    if (textBlocks.isEmpty()) {
                        textBlocks = textBlocks + DesignTextBlock(id = firstId, text = template.suggestedText)
                        selectedBlockId = firstId
                    } else {
                        updateBlock(firstId) { it.copy(text = template.suggestedText) }
                    }
                    selectedAudioOption = template.suggestedAudio
                },
                templates = photoTemplates,
                selectedAudioOption = selectedAudioOption,
                onAudioChange = { selectedAudioOption = it },
                audioOptions = audioOptions,
                brightness = brightness,
                onBrightnessChange = { brightness = it },
                contrast = contrast,
                onContrastChange = { contrast = it },
                saturation = saturation,
                onSaturationChange = { saturation = it },
                selectedCropRatio = selectedCropRatio,
                onCropRatioChange = { selectedCropRatio = it },
                filterId = filterId,
                onFilterChange = { filterId = it },
                backgroundMode = backgroundMode,
                onBackgroundModeChange = { backgroundMode = it },
                bgPresetId = bgPresetId,
                onBgPresetChange = { bgPresetId = it },
                aiBgPath = aiBgPath,
                isGeneratingAi = isGeneratingAi,
                onGenerateAi = {
                    coroutineScope.launch {
                        isGeneratingAi = true
                        progressMessage = "جاري توليد خلفية سينمائية..."
                        try {
                            val topic = textBlocks.firstOrNull()?.text?.take(60) ?: "Islamic spiritual ambiance"
                            val prompt = "Cinematic luxurious Islamic background, $topic, mosque architecture, warm golden light, deep dark slate and gold, soft bokeh, 8k"
                            val generated = AppServices.generateAiImage(prompt)
                            aiBgPath = generated
                            if (generated == null) {
                                Toast.makeText(context, "تعذّر توليد الخلفية الآن — جرّب لاحقاً", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "تعذّر توليد الخلفية: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isGeneratingAi = false
                        }
                    }
                },
                ornamentId = ornamentId,
                onOrnamentChange = { ornamentId = it },
                showWatermark = showWatermark,
                onWatermarkChange = { showWatermark = it },
                outputFormat = outputFormat,
                onOutputFormatChange = { outputFormat = it },
                outputQuality = outputQuality,
                onOutputQualityChange = { outputQuality = it },
                previewPxW = previewPxW,
                previewPxH = previewPxH,
                onPreviewSize = { w, h -> previewPxW = w; previewPxH = h },
                onExportImage = {
                    exportType = "PHOTO"
                    currentStep = 3
                    processing = true
                    progressMessage = "جاري تطبيق المعالجة وتصدير الصورة..."

                    coroutineScope.launch {
                        val scale = when (outputQuality) {
                            "4K" -> 2f
                            "2K" -> 1.333f
                            else -> 1f
                        }
                        val params = ImageAdjustmentParams(
                            brightness = brightness,
                            contrast = contrast,
                            saturation = saturation,
                            cropRatio = selectedCropRatio,
                            filterId = filterId,
                            bgMode = backgroundMode,
                            bgPresetHexes = currentBgPreset?.hexes ?: emptyList(),
                            aiImagePath = aiBgPath,
                            textBlocks = textBlocks.filter { it.text.isNotBlank() },
                            ornamentId = ornamentId,
                            showBrandWatermark = showWatermark,
                            outputFormat = outputFormat,
                            resolutionScale = scale
                        )
                        val outFile = ImageEditorEngine.processAndExportImage(
                            context = context,
                            inputUri = selectedImageUris.firstOrNull(),
                            params = params
                        )
                        processing = false
                        if (outFile != null && outFile.exists()) {
                            exportedPhotoPath = outFile.absolutePath
                            Toast.makeText(context, "تم حفظ وتصدير الصورة بدقة عالية بنجاح ✦", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "حدث خطأ أثناء تصدير الصورة", Toast.LENGTH_LONG).show()
                            currentStep = 2
                        }
                    }
                },
                onExportVideo = {
                    if (selectedImageUris.isEmpty()) {
                        Toast.makeText(context, "تصدير الفيديو يحتاج صورة واحدة على الأقل", Toast.LENGTH_SHORT).show()
                        return@Step2Editor
                    }
                    exportType = "VIDEO"
                    currentStep = 3
                    processing = true
                    progressMessage = "جاري تحضير الفيديو..."
                    coroutineScope.launch {
                        val outPath = File(context.cacheDir, "qabas_studio_${System.currentTimeMillis()}.mp4").absolutePath
                        var audioPathToUse: String? = null
                        if (selectedAudioOption == "مؤثرات طبيعية") {
                            progressMessage = "جاري تحميل المؤثرات..."
                            try {
                                val assetFile = File(context.cacheDir, "activity.mp3")
                                if (!assetFile.exists()) {
                                    context.assets.open("audio/activity.mp3").use { input ->
                                        assetFile.outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                                audioPathToUse = assetFile.absolutePath
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else if (selectedAudioOption == "تلاوة هادئة") {
                            progressMessage = "جاري تحميل التلاوة..."
                            try {
                                val recitationFile = File(context.cacheDir, "recitation_alafasy_255.mp3")
                                if (!recitationFile.exists()) {
                                    withContext(Dispatchers.IO) {
                                        java.net.URL("https://cdn.islamic.network/quran/audio/128/ar.alafasy/255.mp3")
                                            .openStream().use { input ->
                                                recitationFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                    }
                                }
                                audioPathToUse = recitationFile.absolutePath
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        progressMessage = "جاري تجميع الفيديو وإضافة المؤثرات..."
                        val firstText = textBlocks.firstOrNull()?.text ?: ""
                        val success = PhotoStudioEngine.generateVideoFromImages(
                            context,
                            selectedImageUris,
                            firstText.takeIf { it.isNotBlank() },
                            audioPathToUse,
                            outPath
                        )

                        processing = false
                        if (success) {
                            generatedVideoPath = outPath
                        } else {
                            Toast.makeText(context, "حدث خطأ أثناء الإنتاج.", Toast.LENGTH_LONG).show()
                            currentStep = 2
                        }
                    }
                },
                onAnimateWithHiggsfield = {
                    if (selectedImageUris.isEmpty()) {
                        Toast.makeText(context, "التحريك يحتاج صورة واحدة على الأقل", Toast.LENGTH_SHORT).show()
                        return@Step2Editor
                    }
                    exportType = "VIDEO"
                    currentStep = 3
                    processing = true
                    progressMessage = "جاري تهيئة الصورة للإرسال إلى Higgsfield AI..."

                    coroutineScope.launch {
                        val inputUri = selectedImageUris.firstOrNull()
                        val cacheFile = File(context.cacheDir, "higgsfield_input_${System.currentTimeMillis()}.jpg")
                        try {
                            if (inputUri != null) {
                                context.contentResolver.openInputStream(inputUri)?.use { input ->
                                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            val prompt = textBlocks.firstOrNull()?.text?.ifBlank { null }
                                ?: "serene holy atmosphere, golden ambient light, smooth cinematic 3D camera orbit, particles, 8k resolution"

                            val isConfigured = HiggsfieldVideoService.isConfigured(context)
                            val outVideo = if (isConfigured) {
                                HiggsfieldVideoService.animateImage(
                                    context = context,
                                    imageFile = cacheFile,
                                    prompt = prompt,
                                    cameraMotion = "orbit",
                                    onProgress = { p, msg ->
                                        progressMessage = "Higgsfield AI: $msg"
                                    }
                                )
                            } else null

                            if (outVideo != null && outVideo.exists()) {
                                processing = false
                                generatedVideoPath = outVideo.absolutePath
                                Toast.makeText(context, "تم تحريك المشهد بنجاح بواسطة Higgsfield AI! 🎬✨", Toast.LENGTH_LONG).show()
                            } else {
                                if (!isConfigured) {
                                    Toast.makeText(context, "مفتاح Higgsfield غير مسجل — جاري التحريك بالمحرك المحلي 🔄", Toast.LENGTH_LONG).show()
                                }
                                progressMessage = "جاري تطبيق التحريك عبر المحرك السينمائي المحلي..."
                                val fallbackPath = File(context.cacheDir, "higgsfield_fallback_${System.currentTimeMillis()}.mp4").absolutePath
                                val success = PhotoStudioEngine.generateVideoFromImages(
                                    context = context,
                                    imageUris = selectedImageUris,
                                    textOverlay = prompt,
                                    audioPath = null,
                                    outputPath = fallbackPath
                                )
                                processing = false
                                if (success) {
                                    generatedVideoPath = fallbackPath
                                } else {
                                    currentStep = 2
                                    Toast.makeText(context, "تعذر تحريك الصورة.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            processing = false
                            currentStep = 2
                            Toast.makeText(context, "تعذر قراءة ملف الصورة.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            3 -> Step3Export(
                processing = processing,
                progressMessage = progressMessage,
                videoPath = generatedVideoPath,
                photoPath = exportedPhotoPath,
                exportType = exportType,
                outputFormat = outputFormat,
                onShare = {
                    val targetPath = if (exportType == "PHOTO") exportedPhotoPath else generatedVideoPath
                    if (targetPath != null) {
                        val file = File(targetPath)
                        if (file.exists()) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            val mime = if (exportType == "PHOTO") {
                                if (outputFormat == "PNG") "image/png" else "image/jpeg"
                            } else "video/mp4"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الإبداع الدعوي"))
                        }
                    }
                },
                onSaveProject = { path ->
                    Toast.makeText(context, "تم حفظ المشروع في مسوداتك بنجاح", Toast.LENGTH_SHORT).show()
                    onSaveProject(path)
                },
                onNavigateToAdvancedEdit = { path ->
                    onNavigateToAdvancedEdit(path)
                }
            )
        }
    }
}

@Composable
fun Step1ImageSelection(
    selectedImageUris: List<Uri>,
    onSelectMore: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onNext: () -> Unit,
    onNextNoPhoto: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("الخطوة 1: اختيار الصور", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text("اختر صورة للتصميم أو ابدأ فوراً بخلفية مولّدة بدون صور", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.background(Color(0xFF042F2E), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("استوديو عالي الدقة: خطوط عربية، خلفيات، فلاتر، زخارف", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (selectedImageUris.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardSurface)
                    .border(2.dp, GoldPrimary, RoundedCornerShape(24.dp))
                    .clickable { onSelectMore() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Upload", tint = GoldPrimary, modifier = Modifier.size(72.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("انقر لرفع الصور", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("معرض الصور / الكاميرا", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp)
                }
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text("الصور المختارة (${selectedImageUris.size}):", color = TextPrimary, fontFamily = CairoFont, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(selectedImageUris) { uri ->
                        Box(modifier = Modifier.size(130.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { onRemoveImage(uri) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(14.dp))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إزالة", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardSurface)
                                .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .clickable { onSelectMore() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Add, contentDescription = "Add More", tint = GoldPrimary, modifier = Modifier.size(32.dp))
                                Text("إضافة", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNextNoPhoto,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF22D3EE)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE))
        ) {
            Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFF22D3EE))
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصميم فوري بخلفية مولّدة — بدون صور 🎨", color = Color(0xFF22D3EE), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNext,
            enabled = selectedImageUris.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, disabledContainerColor = CardSurface)
        ) {
            Text("متابعة للمحرر", color = if (selectedImageUris.isNotEmpty()) DeepSlate else TextSecondary, fontFamily = CairoFont, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = if (selectedImageUris.isNotEmpty()) DeepSlate else TextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step2Editor(
    selectedImageUris: List<Uri>,
    textBlocks: List<DesignTextBlock>,
    selectedBlockId: Int?,
    onSelectBlock: (Int) -> Unit,
    onAddBlock: () -> Unit,
    onRemoveBlock: (Int) -> Unit,
    onUpdateBlock: (Int, (DesignTextBlock) -> DesignTextBlock) -> Unit,
    selectedTemplate: PhotoTemplate?,
    onTemplateChange: (PhotoTemplate) -> Unit,
    templates: List<PhotoTemplate>,
    selectedAudioOption: String,
    onAudioChange: (String) -> Unit,
    audioOptions: List<String>,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit,
    selectedCropRatio: String,
    onCropRatioChange: (String) -> Unit,
    filterId: String,
    onFilterChange: (String) -> Unit,
    backgroundMode: String,
    onBackgroundModeChange: (String) -> Unit,
    bgPresetId: String,
    onBgPresetChange: (String) -> Unit,
    aiBgPath: String?,
    isGeneratingAi: Boolean,
    onGenerateAi: () -> Unit,
    ornamentId: String,
    onOrnamentChange: (String) -> Unit,
    showWatermark: Boolean,
    onWatermarkChange: (Boolean) -> Unit,
    outputFormat: String,
    onOutputFormatChange: (String) -> Unit,
    outputQuality: String,
    onOutputQualityChange: (String) -> Unit,
    previewPxW: Int,
    previewPxH: Int,
    onPreviewSize: (Int, Int) -> Unit,
    onExportImage: () -> Unit,
    onExportVideo: () -> Unit,
    onAnimateWithHiggsfield: () -> Unit = {}
) {
    var activeTab by remember { mutableStateOf("النص") }
    val tabs = listOf("النص", "الخلفية", "الزخرفة", "قص وضبط", "القالب", "الصوت")
    val cropRatios = listOf("9:16", "1:1", "16:9", "4:5")

    val previewAspectRatio = when (selectedCropRatio) {
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        "16:9" -> 16f / 9f
        "4:5" -> 4f / 5f
        else -> 9f / 16f
    }

    val filterForPreview = remember(brightness, contrast, saturation, filterId) {
        ColorFilter.colorMatrix(composeColorMatrixFrom(designFilterMatrixArray(designFilterById(filterId), brightness, contrast, saturation)))
    }

    val currentBgPreset = when (backgroundMode) {
        "SOLID" -> DesignSolidPresets.firstOrNull { it.id == bgPresetId } ?: DesignSolidPresets[0]
        "GRADIENT" -> DesignGradientPresets.firstOrNull { it.id == bgPresetId } ?: DesignGradientPresets[0]
        else -> null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // منطقة المعاينة
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .background(Color(0xFF030712)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.95f)
                    .aspectRatio(previewAspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .onSizeChanged { onPreviewSize(it.width, it.height) }
            ) {
                // الخلفية
                when (backgroundMode) {
                    "SOLID", "GRADIENT" -> {
                        val colors = designBgColors(currentBgPreset?.hexes ?: listOf("#0B0F19"))
                        val brush = if (colors.size == 1) Brush.verticalGradient(listOf(colors[0], colors[0])) else Brush.verticalGradient(colors)
                        Box(modifier = Modifier.fillMaxSize().background(brush))
                    }
                    "AI" -> {
                        val path = aiBgPath
                        if (path != null && File(path).exists()) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = "AI Background",
                                contentScale = ContentScale.Crop,
                                colorFilter = filterForPreview,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0F19), Color(0xFF3B2A10), Color(0xFF0B0F19)))))
                        }
                    }
                    "BLUR" -> {
                        if (selectedImageUris.isNotEmpty()) {
                            AsyncImage(
                                model = selectedImageUris.first(),
                                contentDescription = "Blurred Photo",
                                contentScale = ContentScale.Crop,
                                colorFilter = filterForPreview,
                                modifier = Modifier.fillMaxSize().blur(14.dp)
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0F19), Color(0xFF1F2937)))))
                        }
                    }
                    else -> {
                        if (selectedImageUris.isNotEmpty()) {
                            AsyncImage(
                                model = selectedImageUris.first(),
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                colorFilter = filterForPreview,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0F19), Color(0xFF3B2A10), Color(0xFF0B0F19)))))
                        }
                    }
                }

                // تظليل سفلي خفيف (للصور والضبابية)
                if (backgroundMode == "PHOTO" || backgroundMode == "BLUR") {
                    Box(modifier = Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                            startY = 250f
                        )
                    ))
                }

                // إطار أساسي
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .border(1.5.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                )

                // الزخرفة (نفس رسم التصدير)
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas?.let { native ->
                            DesignOrnamentRenderer.draw(native, size.width.roundToInt(), size.height.roundToInt(), designOrnamentById(ornamentId))
                        }
                    }
                }

                // نصوص التصميم (قابلة للسحب)
                if (previewPxW > 0 && previewPxH > 0) {
                    textBlocks.forEach { block ->
                        if (block.text.isNotBlank()) {
                            DraggableDesignText(
                                block = block,
                                previewW = previewPxW,
                                previewH = previewPxH,
                                onMove = { nx, ny ->
                                    onUpdateBlock(block.id) { it.copy(posX = nx, posY = ny) }
                                },
                                onClick = { onSelectBlock(block.id) }
                            )
                        }
                    }
                }

                // watermark معاينة
                if (showWatermark) {
                    Text(
                        "✦ قَبَس | QABAS STUDIO ✦",
                        color = GoldPrimary.copy(alpha = 0.8f),
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    )
                }

                // زر التنظيف السريع للنصوص
                FloatingActionButton(
                    onClick = onAddBlock,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp),
                    containerColor = GoldPrimary,
                    contentColor = DeepSlate
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة نص")
                }
            }
        }

        // شريط التبويبات
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(activeTab).coerceAtLeast(0),
            containerColor = Color(0xFF0B0F19),
            edgePadding = 8.dp,
            divider = {}
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = {
                        Text(tab, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal)
                    }
                )
            }
        }

        // محتوى التبويب
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (activeTab) {
                "النص" -> {
                    SelectedBlockTextTab(
                        textBlocks = textBlocks,
                        selectedBlockId = selectedBlockId,
                        onSelectBlock = onSelectBlock,
                        onAddBlock = onAddBlock,
                        onRemoveBlock = onRemoveBlock,
                        onUpdateBlock = onUpdateBlock
                    )
                }
                "الخلفية" -> {
                    Text("وضع الخلفية:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DesignBgModes) { mode ->
                            val isSelected = backgroundMode == mode.id
                            Surface(
                                onClick = { onBackgroundModeChange(mode.id) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) GoldPrimary else CardSurface,
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B))
                            ) {
                                Text(
                                    "${mode.icon} ${mode.label}",
                                    color = if (isSelected) DeepSlate else TextPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    when (backgroundMode) {
                        "SOLID" -> {
                            Text("ألوان سادة:", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(DesignSolidPresets) { preset ->
                                    val isSelected = bgPresetId == preset.id
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(designBgColors(preset.hexes).first())
                                                .border(2.dp, if (isSelected) GoldPrimary else Color.Transparent, RoundedCornerShape(12.dp))
                                                .clickable { onBgPresetChange(preset.id) }
                                        )
                                        Text("${preset.icon} ${preset.label}", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        "GRADIENT" -> {
                            Text("تدرجات فاخرة:", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(DesignGradientPresets) { preset ->
                                    val isSelected = bgPresetId == preset.id
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Brush.verticalGradient(designBgColors(preset.hexes)))
                                                .border(2.dp, if (isSelected) GoldPrimary else Color.Transparent, RoundedCornerShape(12.dp))
                                                .clickable { onBgPresetChange(preset.id) }
                                        )
                                        Text("${preset.icon} ${preset.label}", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        "AI" -> {
                            Text("خلفية سينمائية فائقة الدقة بالذكاء الاصطناعي (FLUX.1 مجاناً بلا مفاتيح):", color = TextPrimary, fontFamily = CairoFont, fontSize = 13.sp)
                            Button(
                                onClick = onGenerateAi,
                                enabled = !isGeneratingAi,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C1E12)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, GoldPrimary)
                            ) {
                                if (isGeneratingAi) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GoldPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("جاري التوليد...", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.Palette, contentDescription = null, tint = GoldPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (aiBgPath != null) "توليد خلفية أخرى 🔄" else "توليد خلفية سينمائية 🎨", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                        "BLUR" -> {
                            Text("ضبابية سينمائية للصورة المختارة (تتطلب صورة).", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                        else -> {
                            Text("الخلفية هي الصورة المختارة مع أدوات القص والضبط.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }
                }
                "الزخرفة" -> {
                    Text("زخرفة التصميم (تظهر في التصدير بالضبط كما في المعاينة):", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DesignOrnament.entries) { orn ->
                            val isSelected = ornamentId == orn.id
                            Surface(
                                onClick = { onOrnamentChange(orn.id) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) GoldPrimary else CardSurface,
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B))
                            ) {
                                Text(
                                    "${orn.icon} ${orn.label}",
                                    color = if (isSelected) DeepSlate else TextPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = showWatermark,
                            onCheckedChange = onWatermarkChange,
                            colors = CheckboxDefaults.colors(checkedColor = GoldPrimary)
                        )
                        Text("علامة قبس المائية", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                }
                "قص وضبط" -> {
                    Text("نسبة الأبعاد:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cropRatios) { ratio ->
                            val isSelected = selectedCropRatio == ratio
                            OutlinedButton(
                                onClick = { onCropRatioChange(ratio) },
                                modifier = Modifier.height(38.dp),
                                border = BorderStroke(1.5.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) GoldPrimary else CardSurface,
                                    contentColor = if (isSelected) DeepSlate else TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(ratio, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    Text("فلتر لوني:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DesignFilter.entries) { f ->
                            val isSelected = filterId == f.id
                            Surface(
                                onClick = { onFilterChange(f.id) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) GoldPrimary else CardSurface,
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B))
                            ) {
                                Text(
                                    f.label,
                                    color = if (isSelected) DeepSlate else TextPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإضاءة", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                        Text("${brightness.toInt()}", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    Slider(
                        value = brightness,
                        onValueChange = onBrightnessChange,
                        valueRange = -50f..50f,
                        colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("التباين", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                        Text(String.format("%.1fx", contrast), color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    Slider(
                        value = contrast,
                        onValueChange = onContrastChange,
                        valueRange = 0.5f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("التشبع اللوني", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                        Text(String.format("%.1fx", saturation), color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                    Slider(
                        value = saturation,
                        onValueChange = onSaturationChange,
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
                    )

                    HorizontalDivider(color = Color(0xFF1E293B))

                    Text("دقة التصدير:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HD", "2K", "4K").forEach { q ->
                            val isSelected = outputQuality == q
                            Surface(
                                onClick = { onOutputQualityChange(q) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) GoldPrimary else CardSurface,
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B))
                            ) {
                                Text(
                                    q,
                                    color = if (isSelected) DeepSlate else TextPrimary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Text("الصيغة:", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("JPEG", "PNG").forEach { fmt ->
                            val isSelected = outputFormat == fmt
                            Surface(
                                onClick = { onOutputFormatChange(fmt) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) GoldPrimary else CardSurface,
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B))
                            ) {
                                Text(
                                    fmt,
                                    color = if (isSelected) DeepSlate else TextPrimary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                "القالب" -> {
                    Text("قوالب المحتوى الدعوي (نصوص موثوقة):", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(templates) { tmpl ->
                            val isSelected = selectedTemplate == tmpl
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) tmpl.color.copy(alpha = 0.2f) else CardSurface)
                                    .border(2.dp, if (isSelected) tmpl.color else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { onTemplateChange(tmpl) }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(tmpl.icon, contentDescription = null, tint = tmpl.color)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(tmpl.title, color = TextPrimary, fontFamily = CairoFont, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(tmpl.description, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                "الصوت" -> {
                    Text("صوت الفيديو (عند تصدير فيديو):", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    audioOptions.forEach { audio ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onAudioChange(audio) }.padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedAudioOption == audio,
                                onClick = { onAudioChange(audio) },
                                colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary, unselectedColor = TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(audio, color = TextPrimary, fontFamily = NotoSansFont, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // أزرار التصدير
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onExportImage,
                    modifier = Modifier.weight(1.2f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = DeepSlate)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصدير صورة ($outputQuality)", color = DeepSlate, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onExportVideo,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    border = BorderStroke(1.dp, GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("فيديو محلي", color = GoldPrimary, fontFamily = CairoFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Higgsfield AI Image-to-Video Button (Cybernetic Glow)
            Button(
                onClick = onAnimateWithHiggsfield,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("higgsfield_animate_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F1E38)
                ),
                border = BorderStroke(1.2.dp, Color(0xFF00F2FE)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.MovieFilter, contentDescription = null, tint = Color(0xFF00F2FE))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "تحريك بالذكاء الاصطناعي (Higgsfield Video AI 🛸)",
                    color = Color(0xFF00F2FE),
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * كتلة نصية واحدة قابلة للسحب في المعاينة — موضعها بين 0..1 يُستخدم نفسه في التصدير.
 */
@Composable
private fun DraggableDesignText(
    block: DesignTextBlock,
    previewW: Int,
    previewH: Int,
    onMove: (Float, Float) -> Unit,
    onClick: () -> Unit
) {
    var measuredW by remember { mutableIntStateOf(0) }
    var measuredH by remember { mutableIntStateOf(0) }
    val theme = designThemeById(block.style.themeId)
    val fontScale = (previewW / 1080f).coerceAtLeast(0.4f)
    val fontSize = (block.style.sizeSp * fontScale).sp
    val align = designTextAlign(block.style.align)

    val anchorX = when (block.style.align) {
        DesignAlign.CENTER -> -(measuredW / 2f)
        DesignAlign.RIGHT -> (previewW * 0.10f) - measuredW
        DesignAlign.LEFT -> -(previewW * 0.10f)
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    ((block.posX * previewW) + anchorX).roundToInt(),
                    ((block.posY * previewH) - (measuredH / 2f)).roundToInt()
                )
            }
            .rotate(block.style.rotation)
            .onSizeChanged { measuredW = it.width; measuredH = it.height }
            .pointerInput(block.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(
                        (block.posX + dragAmount.x / previewW).coerceIn(0.02f, 0.98f),
                        (block.posY + dragAmount.y / previewH).coerceIn(0.02f, 0.98f)
                    )
                }
            }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
    ) {
        Text(
            text = block.text,
            color = theme.color,
            fontFamily = designFontFamily(block.style.font),
            fontWeight = if (block.style.bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = fontSize,
            textAlign = align,
            lineHeight = fontSize * 1.25f,
            modifier = Modifier.widthIn(max = (previewW * 0.8f).dp)
        )
    }
}

@Composable
private fun SelectedBlockTextTab(
    textBlocks: List<DesignTextBlock>,
    selectedBlockId: Int?,
    onSelectBlock: (Int) -> Unit,
    onAddBlock: () -> Unit,
    onRemoveBlock: (Int) -> Unit,
    onUpdateBlock: (Int, (DesignTextBlock) -> DesignTextBlock) -> Unit
) {
    val activeBlock = textBlocks.firstOrNull { it.id == selectedBlockId } ?: textBlocks.firstOrNull()
    if (textBlocks.isEmpty()) {
        OutlinedButton(
            onClick = onAddBlock,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
            border = BorderStroke(1.dp, GoldPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = GoldPrimary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("أضف أول نص للتصميم", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
        }
        return
    }

    // محدد الكتلة
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(textBlocks) { block ->
            val isSelected = activeBlock?.id == block.id
            Surface(
                onClick = { onSelectBlock(block.id) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) GoldPrimary else CardSurface,
                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B))
            ) {
                Text(
                    "نص ${block.id}",
                    color = if (isSelected) DeepSlate else TextPrimary,
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        item {
            OutlinedButton(
                onClick = onAddBlock,
                enabled = textBlocks.size < 3,
                modifier = Modifier.height(34.dp),
                border = BorderStroke(1.dp, Color(0xFF22D3EE)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE)),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(14.dp))
                Text("إضافة (حتى 3)", color = Color(0xFF22D3EE), fontFamily = NotoSansFont, fontSize = 11.sp)
            }
        }
    }

    if (activeBlock != null) {
        OutlinedTextField(
            value = activeBlock.text,
            onValueChange = { newText -> onUpdateBlock(activeBlock.id) { it.copy(text = newText) } },
            label = { Text("نص التصميم (${activeBlock.id})", fontFamily = CairoFont) },
            placeholder = { Text("اكتب الآية أو الحكمة الدعوية...", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            minLines = 2,
            maxLines = 4
        )

        Text("الخط:", color = TextPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DesignFont.entries) { font ->
                val isSelected = activeBlock.style.font == font
                Surface(
                    onClick = { onUpdateBlock(activeBlock.id) { it.copy(style = it.style.copy(font = font)) } },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) GoldPrimary else CardSurface,
                    border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B))
                ) {
                    Text(
                        font.displayName,
                        color = if (isSelected) DeepSlate else TextPrimary,
                        fontFamily = designFontFamily(font),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Text("لون الخط:", color = TextPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DesignTextThemes) { theme ->
                val isSelected = activeBlock.style.themeId == theme.id
                Surface(
                    onClick = { onUpdateBlock(activeBlock.id) { it.copy(style = it.style.copy(themeId = theme.id)) } },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) theme.color.copy(alpha = 0.2f) else CardSurface,
                    border = BorderStroke(1.5.dp, if (isSelected) theme.color else Color(0xFF1E293B))
                ) {
                    Text(
                        theme.label,
                        color = theme.color,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("حجم الخط (${activeBlock.style.sizeSp.toInt()})", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
            Slider(
                value = activeBlock.style.sizeSp,
                onValueChange = { v -> onUpdateBlock(activeBlock.id) { it.copy(style = it.style.copy(sizeSp = v)) } },
                valueRange = 14f..56f,
                modifier = Modifier.width(180.dp),
                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = activeBlock.style.bold,
                    onCheckedChange = { v -> onUpdateBlock(activeBlock.id) { it.copy(style = it.style.copy(bold = v)) } },
                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary)
                )
                Text("خط عريض", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            TextButton(onClick = { onRemoveBlock(activeBlock.id) }) {
                Text("حذف النص 🗑", color = Color(0xFFEF4444), fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text("المحاذاة:", color = TextPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesignAlign.entries.forEach { align ->
                val isSelected = activeBlock.style.align == align
                OutlinedButton(
                    onClick = { onUpdateBlock(activeBlock.id) { it.copy(style = it.style.copy(align = align)) } },
                    modifier = Modifier.height(34.dp),
                    border = BorderStroke(1.5.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) GoldPrimary else CardSurface,
                        contentColor = if (isSelected) DeepSlate else TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(align.label, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("الدوران (${activeBlock.style.rotation.toInt()}°)", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
            Slider(
                value = activeBlock.style.rotation,
                onValueChange = { v -> onUpdateBlock(activeBlock.id) { it.copy(style = it.style.copy(rotation = v)) } },
                valueRange = -30f..30f,
                modifier = Modifier.width(180.dp),
                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary)
            )
        }

        Text("اسحب النص في المعاينة لتحريكه — نفس الموضع يُحفظ في الصورة النهائية.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
    }
}

@Composable
fun Step3Export(
    processing: Boolean,
    progressMessage: String,
    videoPath: String?,
    photoPath: String?,
    exportType: String,
    outputFormat: String,
    onShare: () -> Unit,
    onSaveProject: (String) -> Unit,
    onNavigateToAdvancedEdit: (String) -> Unit
) {
    val resultPath = if (exportType == "PHOTO") photoPath else videoPath

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (processing) {
            CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("جاري الإنتاج والمعالجة...", color = TextPrimary, fontFamily = CairoFont, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(progressMessage, color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("يتم تجهيز المخرج وفق أعلى معايير الجودة", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
        } else if (resultPath != null) {
            Icon(Icons.Default.CheckCircle, contentDescription = "نجاح", tint = Color(0xFF10B981), modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                if (exportType == "PHOTO") "تم تصدير وحفظ الصورة بنجاح! 🖼️" else "تم تجهيز الفيديو بنجاح! 🎬",
                color = TextPrimary,
                fontFamily = CairoFont,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (exportType == "PHOTO" && photoPath != null) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, GoldPrimary, RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = "Exported Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = DeepSlate)
                Spacer(modifier = Modifier.width(10.dp))
                Text("مشاركة الصورة والتصميم", color = DeepSlate, fontFamily = CairoFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onSaveProject(resultPath) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = BorderStroke(1.dp, TextSecondary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = TextPrimary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("حفظ في مشاريعي والعودة", fontFamily = CairoFont, fontSize = 15.sp)
            }

            if (exportType == "VIDEO") {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onNavigateToAdvancedEdit(resultPath) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    border = BorderStroke(1.dp, GoldPrimary)
                ) {
                    Icon(Icons.Default.MovieFilter, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("نقل إلى المونتاج المتقدم", fontFamily = CairoFont, fontSize = 15.sp, color = GoldPrimary)
                }
            }
        }
    }
}