package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SeriesEpisode(
    val episodeNumber: Int,
    val title: String,
    val hook: String,
    val bodySummary: String,
    val epilogCliffhanger: String,
    val estimatedDuration: String = "60 ثانية",
    var isExpanded: Boolean = false
)

data class SeriesTopicPreset(
    val id: String,
    val title: String,
    val description: String,
    val defaultEpisodeCount: Int,
    val iconEmoji: String,
    val sampleTopic: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSeriesGeneratorDialog(
    context: Context,
    initialSeriesTitle: String = "سلسلة صحابة رسول الله ﷺ",
    onDismiss: () -> Unit = {},
    onExportSeriesToProjects: (List<SeriesEpisode>) -> Unit = {},
    onStartSingleEpisode: (SeriesEpisode, String) -> Unit = { _, _ -> }
) {
    var seriesTitle by remember { mutableStateOf(initialSeriesTitle) }
    var episodeCount by remember { mutableIntStateOf(5) }
    var selectedTargetFormat by remember { mutableStateOf("Shorts / TikTok (9:16)") }
    var selectedFrequency by remember { mutableStateOf("يومياً (Daily)") }
    var defaultEpilogText by remember { mutableStateOf("✦ انتظرونا في الجزء القادم - تابع الحساب للقصة التالية! ✦") }
    var isGeneratingSeries by remember { mutableStateOf(false) }
    var generatedEpisodes by remember { mutableStateOf<List<SeriesEpisode>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: الإعداد والتوليد, 1: الحلقات الناتجة, 2: الأرشيف المجدول

    val scope = rememberCoroutineScope()
    val seriesRepo = remember { SeriesRepository(AppDatabase.getDatabase(context).seriesDao()) }
    val projectRepo = remember { ProjectRepository(AppDatabase.getDatabase(context).projectDao()) }
    val savedSeriesList by seriesRepo.allSeries.collectAsStateWithLifecycle(initialValue = emptyList())

    val topicPresets = remember {
        listOf(
            SeriesTopicPreset(
                id = "s1",
                title = "قصص الصحابة والتابعين",
                description = "حلقات متسلسلة عن مواقف العظماء وأثرهم في التاريخ",
                defaultEpisodeCount = 5,
                iconEmoji = "⚔️",
                sampleTopic = "سلسلة سير المبشرين بالجنة ومواقف خالدة"
            ),
            SeriesTopicPreset(
                id = "s2",
                title = "تدبر وتفسير السور",
                description = "سلسلة لتفسير ومعاني آيات سورة قصيرة أو موضوع قرآني",
                defaultEpisodeCount = 7,
                iconEmoji = "📖",
                sampleTopic = "سلسلة تدبر آيات سورة الكهف وأسرارها"
            ),
            SeriesTopicPreset(
                id = "s3",
                title = "أسماء الله الحسنى ومعانيها",
                description = "رحلة إيمانية في شرح اسم من أسماء الله وأثره في حياتك",
                defaultEpisodeCount = 10,
                iconEmoji = "✨",
                sampleTopic = "سلسلة أسرار أسماء الله الحسنى في استجابة الدعاء"
            ),
            SeriesTopicPreset(
                id = "s4",
                title = "ردود وشبهات معاصرة",
                description = "تفكيك الشبهات خطوة بخطوة في حلقات قصيرة مركزة بالدليل",
                defaultEpisodeCount = 4,
                iconEmoji = "🛡️",
                sampleTopic = "سلسلة دحض شبهات المشككين بالدليل العقلي والشرعي"
            ),
            SeriesTopicPreset(
                id = "s5",
                title = "أخلاق ونظرات نبي الرحمة",
                description = "مشاهد إنسانية من السيرة النبوية العطرة للشباب",
                defaultEpisodeCount = 6,
                iconEmoji = "🕌",
                sampleTopic = "سلسلة أخلاق النبي ﷺ في التعامل مع الناس"
            )
        )
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = GoldPrimary.copy(alpha = 0.15f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, GoldPrimary)
                        ) {
                            Icon(
                                Icons.Default.MovieFilter,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "محرك السلاسل الدعوية الآلي 🚀",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Auto Series & Epilogs Generator",
                                color = TextSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    TabButton(
                        title = "1. الإعداد",
                        isSelected = selectedTab == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 0 }
                    )
                    TabButton(
                        title = "2. الحلقات (${generatedEpisodes.size})",
                        isSelected = selectedTab == 1,
                        modifier = Modifier.weight(1.2f),
                        onClick = { selectedTab = 1 }
                    )
                    TabButton(
                        title = "3. الأرشيف (${savedSeriesList.size})",
                        isSelected = selectedTab == 2,
                        modifier = Modifier.weight(1.2f),
                        onClick = { selectedTab = 2 }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        // CONFIGURATION TAB
                        Text(
                            "اختر قالباً جاهزاً أو أدخل موضوعك الخاص:",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(topicPresets) { preset ->
                                Surface(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .clickable {
                                            seriesTitle = preset.sampleTopic
                                            episodeCount = preset.defaultEpisodeCount
                                        },
                                    color = Color(0xFF151B2B),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (seriesTitle == preset.sampleTopic) GoldPrimary else Color(0xFF1E293B)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("${preset.iconEmoji} ${preset.title}", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(preset.description, color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 14.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Title Input
                        OutlinedTextField(
                            value = seriesTitle,
                            onValueChange = { seriesTitle = it },
                            label = { Text("اسم السلسلة / عنوان الموضوع الشامل", color = Color.Gray, fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Episode Count Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("عدد الحلقات المطلوبة:", color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(3, 5, 7, 10).forEach { count ->
                                    FilterChip(
                                        selected = episodeCount == count,
                                        onClick = { episodeCount = count },
                                        label = { Text("$count حلقات", fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldPrimary,
                                            selectedLabelColor = DeepSlate,
                                            containerColor = Color(0xFF151B2B),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Target Format Selection
                        Text("مقاس الفيديو وتنسيق العرض:", color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            val formats = listOf("Shorts / TikTok (9:16)", "YouTube (16:9)", "Instagram (4:5)")
                            formats.forEach { fmt ->
                                FilterChip(
                                    selected = selectedTargetFormat == fmt,
                                    onClick = { selectedTargetFormat = fmt },
                                    label = { Text(fmt, fontSize = 12.sp, fontFamily = NotoSansFont) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldPrimary,
                                        selectedLabelColor = DeepSlate,
                                        containerColor = Color(0xFF151B2B),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Frequency Schedule
                        Text("وتيرة النشر والجدولة:", color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            val freqs = listOf("يومياً (Daily)", "كل يومين", "أسبوعياً")
                            freqs.forEach { frq ->
                                FilterChip(
                                    selected = selectedFrequency == frq,
                                    onClick = { selectedFrequency = frq },
                                    label = { Text(frq, fontSize = 12.sp, fontFamily = NotoSansFont) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldPrimary,
                                        selectedLabelColor = DeepSlate,
                                        containerColor = Color(0xFF151B2B),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Epilog Template
                        OutlinedTextField(
                            value = defaultEpilogText,
                            onValueChange = { defaultEpilogText = it },
                            label = { Text("عبارة التشويق والربط بالحلقة القادمة (Epilog Callout)", color = Color.Gray, fontFamily = CairoFont) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Generate Button
                        Button(
                            onClick = {
                                if (seriesTitle.isBlank()) {
                                    Toast.makeText(context, "الرجاء إدخال اسم السلسلة أولاً", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isGeneratingSeries = true
                                scope.launch {
                                    val episodes = withContext(Dispatchers.IO) {
                                        generateSeriesOutline(seriesTitle, episodeCount, defaultEpilogText, selectedTargetFormat)
                                    }
                                    generatedEpisodes = episodes
                                    
                                    // Save to Room DB automatically
                                    val jsonArray = JSONArray()
                                    episodes.forEach { ep ->
                                        val obj = JSONObject()
                                        obj.put("episodeNumber", ep.episodeNumber)
                                        obj.put("title", ep.title)
                                        obj.put("hook", ep.hook)
                                        obj.put("bodySummary", ep.bodySummary)
                                        obj.put("epilogCliffhanger", ep.epilogCliffhanger)
                                        obj.put("estimatedDuration", ep.estimatedDuration)
                                        jsonArray.put(obj)
                                    }
                                    
                                    val entity = SeriesEntity(
                                        id = UUID.randomUUID().toString(),
                                        title = seriesTitle,
                                        topicPreset = "سلسلة ذكية",
                                        episodeCount = episodeCount,
                                        format = selectedTargetFormat,
                                        epilogTemplate = defaultEpilogText,
                                        episodesJson = jsonArray.toString(),
                                        scheduleFrequency = selectedFrequency,
                                        status = "READY"
                                    )
                                    seriesRepo.insert(entity)

                                    isGeneratingSeries = false
                                    selectedTab = 1
                                    Toast.makeText(
                                        context,
                                        "تم توليد وحفظ السلسلة ($episodeCount حلقات) في قاعدة البيانات! 🚀",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isGeneratingSeries) {
                                CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("جاري معالجة وتوليد السلسلة بالذكاء الاصطناعي...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "توليد السلسلة كاملة بضغطة زر ✦ ($episodeCount حلقات)",
                                    color = DeepSlate,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    1 -> {
                        // EPISODES RESULT TAB
                        if (generatedEpisodes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .background(Color(0xFF0B0F19), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("لم يتم توليد أي حلقات بعد", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("عد للتبويب الأول واضغط على زر توليد السلسلة", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = GoldPrimary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("السلسلة: $seriesTitle", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("إجمالي الحلقات: ${generatedEpisodes.size} مقطع | وتيرة النشر: $selectedFrequency", color = Color.LightGray, fontFamily = NotoSansFont, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                generatedEpisodes.forEachIndexed { index, ep ->
                                    EpisodeCardItem(
                                        episode = ep,
                                        totalEpisodes = generatedEpisodes.size,
                                        onCopyScript = {
                                            val fullText = "الحلقة ${ep.episodeNumber}: ${ep.title}\n\nالخطاف:\n${ep.hook}\n\nالمضمون:\n${ep.bodySummary}\n\nالختام:\n${ep.epilogCliffhanger}"
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Episode Script", fullText)
                                            clipboard?.setPrimaryClip(clip)
                                            Toast.makeText(context, "تم نسخ نص الحلقة ${ep.episodeNumber} للحافظة ✦", Toast.LENGTH_SHORT).show()
                                        },
                                        onProduceEpisode = {
                                            // 1. Create and save project to Room DB
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val projId = UUID.randomUUID().toString()
                                                    val projectEntity = ProjectEntity(
                                                        id = projId,
                                                        title = ep.title,
                                                        idea = "${ep.hook} - ${ep.bodySummary}",
                                                        analysis = "{\"suggestedStyle\":\"سلسلة دعوية\",\"goal\":\"أثر وإلهام\"}",
                                                        resources = "{}",
                                                        settings = "{\"format\":\"$selectedTargetFormat\"}",
                                                        script = "{\"hook\":\"${ep.hook}\",\"body\":\"${ep.bodySummary}\",\"epilog\":\"${ep.epilogCliffhanger}\"}",
                                                        finalVideoPath = "",
                                                        cost = 0.0,
                                                        createdAt = System.currentTimeMillis(),
                                                        updatedAt = System.currentTimeMillis(),
                                                        status = "DRAFT",
                                                        lastScreen = "IdeaInput"
                                                    )
                                                    projectRepo.insert(projectEntity)
                                                }
                                                onStartSingleEpisode(ep, selectedTargetFormat)
                                                onDismiss()
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Batch Action: Export All To Room Projects
                            Button(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            generatedEpisodes.forEach { ep ->
                                                val projId = UUID.randomUUID().toString()
                                                val projectEntity = ProjectEntity(
                                                    id = projId,
                                                    title = ep.title,
                                                    idea = "${ep.hook} - ${ep.bodySummary}",
                                                    analysis = "{\"suggestedStyle\":\"سلسلة دعوية\",\"goal\":\"أثر وإلهام\"}",
                                                    resources = "{}",
                                                    settings = "{\"format\":\"$selectedTargetFormat\"}",
                                                    script = "{\"hook\":\"${ep.hook}\",\"body\":\"${ep.bodySummary}\",\"epilog\":\"${ep.epilogCliffhanger}\"}",
                                                    finalVideoPath = "",
                                                    cost = 0.0,
                                                    createdAt = System.currentTimeMillis(),
                                                    updatedAt = System.currentTimeMillis(),
                                                    status = "DRAFT",
                                                    lastScreen = "IdeaInput"
                                                )
                                                projectRepo.insert(projectEntity)
                                            }
                                        }
                                        onExportSeriesToProjects(generatedEpisodes)
                                        Toast.makeText(context, "تم تصدير ${generatedEpisodes.size} حلقة كمشاريع جاهزة للإنتاج في الاستوديو ✦", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = DeepSlate)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تصدير الحلقات كمشاريع إنتاجية (${generatedEpisodes.size} مشاريع)", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    2 -> {
                        // SAVED SERIES ARCHIVE TAB
                        if (savedSeriesList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(Color(0xFF0B0F19), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("لا توجد سلاسل محفوظة في قاعدة البيانات", color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                savedSeriesList.forEach { series ->
                                    SavedSeriesCardItem(
                                        series = series,
                                        onLoad = {
                                            try {
                                                val array = JSONArray(series.episodesJson)
                                                val list = mutableListOf<SeriesEpisode>()
                                                for (i in 0 until array.length()) {
                                                    val obj = array.getJSONObject(i)
                                                    list.add(
                                                        SeriesEpisode(
                                                            episodeNumber = obj.optInt("episodeNumber", i + 1),
                                                            title = obj.optString("title", ""),
                                                            hook = obj.optString("hook", ""),
                                                            bodySummary = obj.optString("bodySummary", ""),
                                                            epilogCliffhanger = obj.optString("epilogCliffhanger", ""),
                                                            estimatedDuration = obj.optString("estimatedDuration", "60 ثانية")
                                                        )
                                                    )
                                                }
                                                seriesTitle = series.title
                                                episodeCount = series.episodeCount
                                                generatedEpisodes = list
                                                selectedTab = 1
                                                Toast.makeText(context, "تم استعادة السلسلة بنجاح!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "تعذر قراءة بيانات السلسلة", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDelete = {
                                            scope.launch {
                                                seriesRepo.delete(series.id)
                                                Toast.makeText(context, "تم حذف السلسلة", Toast.LENGTH_SHORT).show()
                                            }
                                        }
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

@Composable
fun SavedSeriesCardItem(
    series: SeriesEntity,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    series.title,
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${series.episodeCount} حلقات",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = CairoFont
                    )
                    Text("•", color = Color.Gray)
                    Text(
                        series.scheduleFrequency,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = CairoFont
                    )
                }
            }

            Row {
                IconButton(onClick = onLoad) {
                    Icon(Icons.Default.Visibility, contentDescription = "استعراض", tint = GoldPrimary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color.Red.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun TabButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) GoldPrimary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            color = if (isSelected) DeepSlate else Color.Gray,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun EpisodeCardItem(
    episode: SeriesEpisode,
    totalEpisodes: Int,
    onCopyScript: () -> Unit,
    onProduceEpisode: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = GoldPrimary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "حلقة ${episode.episodeNumber}/$totalEpisodes",
                            color = DeepSlate,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        episode.title,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "التفاصيل",
                        tint = GoldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Hook line
            Text(
                "الخطاف: ${episode.hook}",
                color = GoldPrimary,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (expanded) {
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(8.dp))

                Text("مضمون الحلقة السريع:", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                Text(episode.bodySummary, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 18.sp)

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("ربط الحلقة القادمة (Cliffhanger):", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(episode.epilogCliffhanger, color = Color.LightGray, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyScript,
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ النص", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    }

                    Button(
                        onClick = onProduceEpisode,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إنتاج هذه الحلقة 🎬", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onProduceEpisode,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إنتاج الحلقة ✦", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Helper to generate real series breakdown via Gemini AI
suspend fun generateSeriesOutline(
    seriesTitle: String,
    count: Int,
    epilogCallout: String,
    format: String = "Reels / Shorts (9:16)"
): List<SeriesEpisode> {
    val prompt = """
أنت كاتب سيناريو ومخرج محتوى دعوي وإسلامي محترف ومسؤول عن تخطيط سلاسل الفيديو الفيروسية (Reels / Shorts / YouTube).
المطلوب إنشاء خطة سلسلة متكاملة بعنوان: "$seriesTitle"
مكونة من: $count حلقات.
تنسيق الفيديو: $format.
عبارة الربط والتشويق المقترحة: "$epilogCallout"

قم بتوليد تفاصيل كل حلقة بدقة بتنسيق JSON حصراً بدون markdown كالتالي:
[
  {
    "episodeNumber": 1,
    "title": "عنوان الحلقة الجذاب والمحدد",
    "hook": "الخطاف الافتتاحي القوي والمشوق (أول 3 ثوانٍ)",
    "bodySummary": "ملخص المضمون والسرد القصصي والأدلة الشرعية للحلقة",
    "epilogCliffhanger": "عبارة التشويق والربط بالحلقة القادمة",
    "estimatedDuration": "45-60 ثانية"
  }
]
""".trimIndent()

    try {
        val response = FirebaseAiService.generateText(prompt)
        val cleanJson = response.replace("```json", "").replace("```", "").trim()
        val startIndex = cleanJson.indexOf('[')
        val endIndex = cleanJson.lastIndexOf(']')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            val jsonArrayStr = cleanJson.substring(startIndex, endIndex + 1)
            val array = JSONArray(jsonArrayStr)
            val list = mutableListOf<SeriesEpisode>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SeriesEpisode(
                        episodeNumber = obj.optInt("episodeNumber", i + 1),
                        title = obj.optString("title", "الحلقة ${i + 1}"),
                        hook = obj.optString("hook", "خطاف مشوق للحلقة"),
                        bodySummary = obj.optString("bodySummary", "مضمون الحلقة"),
                        epilogCliffhanger = obj.optString("epilogCliffhanger", epilogCallout),
                        estimatedDuration = obj.optString("estimatedDuration", "60 ثانية")
                    )
                )
            }
            if (list.isNotEmpty()) return list
        }
    } catch (e: Exception) {
        android.util.Log.e("SeriesEngine", "Gemini series generation fallback: ${e.message}")
    }

    // Fallback if offline or API error
    val list = mutableListOf<SeriesEpisode>()
    val sampleHooks = listOf(
        "هل تعلم ما كان أول عمل قام به بعد دخوله الإسلام؟",
        "موقف عظيم غير مجرى التاريخ ولن تصدق كيف انتهى!",
        "آية واحدة في القرآن تغير نظرتك تماماً للحياة والأمل",
        "لماذا بكى عمر بن الخطاب عندما سمع هذه الكلمات؟",
        "السر الحقيقي وراء الثبات في أوقات الشدة والابتلاء",
        "الرد القاطع الذي أذهل العلماء والمفكرين في عصره",
        "معجزة نبوية يكتشف العلم الحديث صحتها اليوم!",
        "دعاء قصير كفيل بأن يرفع عنك الهم والغم فوراً",
        "قصة صحابي جليل ضحى بكل ما يملك من أجل الحق",
        "كيف تتعامل مع البلاء بحكمة وهدوء كما علمنا النبي ﷺ"
    )

    for (i in 1..count) {
        val epTitle = when {
            seriesTitle.contains("صحابة") || seriesTitle.contains("قصص") -> "الجزء $i: موقف ومأثرة من السيرة المباركة ($i)"
            seriesTitle.contains("تدبر") || seriesTitle.contains("سور") -> "الجزء $i: أسرار وإعجاز الآيات ($i)"
            seriesTitle.contains("أسماء") || seriesTitle.contains("الله") -> "الجزء $i: أسرار وتجليات الاسم المبارك ($i)"
            seriesTitle.contains("شبهات") -> "الجزء $i: تفكيك وتفنيد الشبهة بالأدلة ($i)"
            else -> "الحلقة $i: $seriesTitle"
        }

        val hook = sampleHooks[(i - 1) % sampleHooks.size]
        val body = "في هذه الحلقة نتناول بالتفصيل المواقف الموثقة والأدلة الشرعية بأسلوب قصصي جذاب يناسب منصات الفيديو القصيرة، مع التركيز على استخلاص العبرة والدروس التربوية الإيمانية."
        val epilog = if (i < count) {
            "✦ انتظرونا في الجزء القادم (الحلقة ${i + 1}) لتكتمل القصة - $epilogCallout"
        } else {
            "✦ وبهذا نكون قد أتممنا هذه السلسلة المباركة! شارك المقطع ليعم النفع والخير ✦"
        }

        list.add(
            SeriesEpisode(
                episodeNumber = i,
                title = epTitle,
                hook = hook,
                bodySummary = body,
                epilogCliffhanger = epilog
            )
        )
    }
    return list
}
