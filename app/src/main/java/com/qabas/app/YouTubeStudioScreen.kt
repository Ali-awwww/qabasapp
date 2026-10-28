package com.qabas.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.NotoSansFont
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Identifies which YouTube tool the user just launched. */
private enum class YouTubeTool { SILENCE, AUDIO_CLEAN, CHAPTERS, SUBTITLES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeStudioScreen(
    onBack: () -> Unit,
    onStartSeries: () -> Unit = {},
    onStartEpisode: (title: String, scriptText: String, format: String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var showThumbnailDialog by remember { mutableStateOf(false) }
    var showSeriesDialog by remember { mutableStateOf(false) }

    // State for the five new FFmpeg-backed tools.
    // The picker fires the launchActivityForResult; once the user chooses a
    // video we route it into the right YouTubeStudioEngine function.
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var activeTool by remember { mutableStateOf<YouTubeTool?>(null) }
    var processingLabel by remember { mutableStateOf("") }
    var processingDone by remember { mutableStateOf(false) }
    var processingResult by remember { mutableStateOf<String?>(null) }

    // Video is required for tools that operate on footage.
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingVideoUri = uri
    }

    fun launchTool(tool: YouTubeTool) {
        val label = when (tool) {
            YouTubeTool.SILENCE -> "إزالة الصمت"
            YouTubeTool.AUDIO_CLEAN -> "تصفية الصوت"
            YouTubeTool.CHAPTERS -> "توليد الفصول الزمنية"
            YouTubeTool.SUBTITLES -> "إضافة ترجمة"
        }
        processingResult = null
        processingDone = false
        processingLabel = label
        activeTool = tool
    }

    // When the picker delivers a uri, dispatch to the engine.
    LaunchedEffect(pendingVideoUri, activeTool) {
        val uri = pendingVideoUri ?: return@LaunchedEffect
        val tool = activeTool ?: return@LaunchedEffect
        pendingVideoUri = null  // consume
        // Copy the picked video into cache so the engine reads a stable path.
        val inputFile = File(context.cacheDir, "yt_input_${System.currentTimeMillis()}.mp4")
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                inputFile.outputStream().use { input.copyTo(it) }
            }
        }
        if (!inputFile.exists() || inputFile.length() == 0L) {
            processingResult = "تعذّر قراءة ملف الفيديو. تأكد من صلاحيات الوصول."
            processingDone = true
            return@LaunchedEffect
        }
        val inputPath = inputFile.absolutePath
        val outputPath = File(context.cacheDir, "yt_output_${System.currentTimeMillis()}.mp4").absolutePath
        val result: Any = when (tool) {
            YouTubeTool.SILENCE -> YouTubeStudioEngine.removeSilence(context, inputPath, outputPath)
            YouTubeTool.AUDIO_CLEAN -> YouTubeStudioEngine.cleanupAudio(context, inputPath, outputPath)
            YouTubeTool.CHAPTERS -> YouTubeStudioEngine.generateChapters(context, inputPath) ?: ""
            YouTubeTool.SUBTITLES -> YouTubeStudioEngine.burnSubtitles(
                context,
                inputPath,
                caption = "اشترك في القناة وفعّل الجرس 🔔",
                outputPath = outputPath
            )
        }
        processingResult = when (tool) {
            YouTubeTool.CHAPTERS ->
                if ((result as String).isBlank()) "لم يتم العثور على فصول زمنية في الفيديو." else "تم حفظ الفصول الزمنية: $result"
            YouTubeTool.SILENCE, YouTubeTool.AUDIO_CLEAN, YouTubeTool.SUBTITLES ->
                if (result as Boolean) "تم! الناتج محفوظ في: $outputPath" else "فشلت العملية. راجع السجلات."
        }
        processingDone = true
    }

    if (showThumbnailDialog) {
        AutoThumbnailGeneratorDialog(
            context = LocalContext.current,
            initialTitle = "استراتيجية نمو القنوات والدعوة بالذكاء الاصطناعي",
            onDismiss = { showThumbnailDialog = false }
        )
    }

    if (showSeriesDialog) {
        AutoSeriesGeneratorDialog(
            context = LocalContext.current,
            initialSeriesTitle = "سلسلة صحابة رسول الله ﷺ",
            onDismiss = { showSeriesDialog = false },
            onExportSeriesToProjects = { episodes ->
                showSeriesDialog = false
                onStartSeries()
            },
            onStartSingleEpisode = { ep, format ->
                showSeriesDialog = false
                val fullScript = "الحلقة ${ep.episodeNumber}: ${ep.title}\n\nالخطاف:\n${ep.hook}\n\nالمضمون:\n${ep.bodySummary}\n\nالختام:\n${ep.epilogCliffhanger}"
                onStartEpisode(ep.title, fullScript, format)
            }
        )
    }

    // Processing dialog shown while a YouTube tool runs and again with the
    // final result once FFmpeg finishes.
    if (activeTool != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            activeTool = null
            processingDone = false
            processingResult = null
        }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        processingLabel,
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (pendingVideoUri == null && !processingDone) {
                        Text(
                            "اختر ملف فيديو للمعالجة",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                videoPicker.launch(arrayOf("video/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = DeepSlate)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("اختيار فيديو", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    } else if (!processingDone) {
                        CircularProgressIndicator(color = GoldPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "جاري المعالجة بواسطة FFmpeg…",
                            color = Color.Gray,
                            fontFamily = NotoSansFont,
                            fontSize = 13.sp
                        )
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            processingResult ?: "تمت العملية.",
                            color = Color.White,
                            fontFamily = NotoSansFont,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = {
                            activeTool = null
                            processingDone = false
                            processingResult = null
                        }) { Text("إغلاق", color = GoldPrimary, fontFamily = CairoFont) }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = DeepSlate,
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Translator.tr("استوديو يوتيوب الذكي"), color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            color = Color(0xFF064E3B).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Text(
                                "Gemini 3.8 Flash (Firebase AI Logic) ✦",
                                color = Color(0xFF6EE7B7),
                                fontSize = 9.sp,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Translator.tr("العودة"), tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F0F))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Stats
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141414))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            Translator.tr("لا تتوفر إحصائيات حقيقية بعد"),
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            Translator.tr("لم تُربط الشاشة بحساب يوتيوب فعلياً — تُعرض الأرقام الحقيقية عند الربط."),
                            color = Color.Gray,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Series Progress
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GoldPrimary.copy(alpha = 0.1f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable { showSeriesDialog = true }
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.OndemandVideo, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(Translator.tr("مولد السلاسل والروابط التلقائية (Auto Series Engine) 🚀"), color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(Translator.tr("إنشاء حزم فيديوهات متسلسلة تلقائياً مع ربط الحلقة القادمة بضغطة زر"), color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            // Tools Section
            item {
                Text(Translator.tr("أدوات الذكاء الاصطناعي للفيديوهات الطويلة"), color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            item {
                val tools = listOf(
                    ToolItem(Translator.tr("صورة وعنوان"), Translator.tr("توليد صورة مصغرة جذابة وعناوين يوتيوب مقترحة للفت الانتباه."), Icons.Default.Image, Color(0xFFF44336), "thumbnail"),
                    ToolItem(Translator.tr("إزالة الصمت"), Translator.tr("إزالة تلقائية للوقفات الطويلة والمترددة لزيادة التفاعل."), Icons.Default.ContentCut, Color(0xFFE91E63), "silence"),
                    ToolItem(Translator.tr("تصفية الصوت"), Translator.tr("إزالة ضجيج الخلفية وتضخيم الصوت ليكون بجودة البودكاست."), Icons.Default.GraphicEq, Color(0xFF2196F3), "audio"),
                    ToolItem(Translator.tr("الفصول الزمنية"), Translator.tr("توليد فصول وعناوين لليوتيوب تلقائياً مع التوقيت."), Icons.Default.FormatListNumbered, Color(0xFFFF9800), "chapters"),
                    ToolItem(Translator.tr("ترجمة احترافية"), Translator.tr("إضافة نص تفاعلي متحرك أسفل الفيديو بـ 30 لغة."), Icons.Default.Subtitles, Color(0xFF4CAF50), "subtitles")
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.heightIn(max = 800.dp),
                    userScrollEnabled = false
                ) {
                    items(tools) { tool ->
                        ToolCard(tool = tool, onClick = {
                            when (tool.id) {
                                "thumbnail" -> showThumbnailDialog = true
                                "silence"    -> launchTool(YouTubeTool.SILENCE)
                                "audio"      -> launchTool(YouTubeTool.AUDIO_CLEAN)
                                "chapters"   -> launchTool(YouTubeTool.CHAPTERS)
                                "subtitles"  -> launchTool(YouTubeTool.SUBTITLES)
                            }
                        })
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Translator.tr("المشاريع الطويلة الأخيرة"),
                        color = Color.White,
                        fontFamily = TajawalFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141414))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        Translator.tr("لا توجد مشاريع محفوظة بعد — أنشئ أول مشروع من أدوات الاستوديو أعلاه."),
                        color = Color.Gray,
                        fontFamily = NotoSansFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showSeriesDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSlate),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Translator.tr("بدء سلسلة محتوى جديدة بنفس النمط"), color = GoldPrimary, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

data class ToolItem(val title: String, val desc: String, val icon: ImageVector, val color: Color, val id: String = "")

@Composable
fun ToolCard(tool: ToolItem, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(tool.color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(tool.icon, contentDescription = null, tint = tool.color, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(tool.title, color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(tool.desc, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 16.sp)
    }
}

@Composable
fun ThumbnailGeneratorDialog(onDismiss: () -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var generatedImage by remember { mutableStateOf<String?>(null) }
    var suggestedTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(Translator.tr("مولّد العناوين والأفكار"), color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(Translator.tr("ارفع نسبة النقر إلى الظهور (CTR) من خلال عناوين وصور مصغرة احترافية."), color = Color.Gray, fontFamily = NotoSansFont, fontSize = 14.sp, textAlign = TextAlign.Center)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(Translator.tr("موضوع الفيديو (مثال: أثر الصدقة)"), color = Color.Gray, fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        isGenerating = true
                        generatedImage = null
                        suggestedTitles = emptyList()
                        scope.launch {
                            val titlesText = runCatching { AppServices.generateTitles(prompt) }.getOrDefault("")
                            val titles = titlesText
                                .lineSequence()
                                .map { it.trim().trimStart('1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '.', ')', '-', '•') }
                                .filter { it.isNotBlank() && it.length >= 8 }
                                .take(3)
                                .toList()
                            if (titles.isEmpty()) {
                                generatedImage = null
                                android.widget.Toast.makeText(context, "تعذر توليد عناوين حقيقية حالياً — حاول مجدداً", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                suggestedTitles = titles
                            }
                            isGenerating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isGenerating && prompt.isNotBlank()
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = DeepSlate)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Translator.tr("توليد العناوين"), color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
                
                if (suggestedTitles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(Translator.tr("العناوين المقترحة:"), color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))
                    suggestedTitles.forEach { title ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Title, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title, color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp)
                        }
                    }
                }

                if (generatedImage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(Translator.tr("الصورة المصغرة المقترحة:"), color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = generatedImage,
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!isGenerating && suggestedTitles.isEmpty() && prompt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        Translator.tr("توليد الصور المصغرة بالذكاء الاصطناعي غير متاح حالياً — تُولَّد العناوين فقط."),
                        color = Color.Gray,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(Translator.tr("إغلاق"), color = Color.Gray, fontFamily = CairoFont)
                }
            }
        }
    }
}
