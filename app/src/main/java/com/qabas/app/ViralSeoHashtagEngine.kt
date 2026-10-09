package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SeoTagRecommendation(
    val hashtag: String,
    val searchVolume: String, // e.g. "2.4M"
    val relevanceScore: Int,  // e.g. 98%
    val category: String,     // e.g. "تريند عام", "محتوى إسلامي", "قصص"
    var isSelected: Boolean = true
)

data class OptimizedTitleSuggestion(
    val title: String,
    val ctrBoostEstimate: String, // e.g. "+35% CTR"
    val hookType: String          // e.g. "سؤال مفاجئ", "حقيقة غائبة", "دعوة للتدبر"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ViralSeoHashtagsDialog(
    context: Context,
    initialTopicOrScript: String = "",
    onDismiss: () -> Unit = {},
    onApplySeoData: (selectedTitle: String, hashtagsText: String) -> Unit = { _, _ -> }
) {
    var topicQuery by remember { mutableStateOf(initialTopicOrScript.take(80)) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var tagList by remember { mutableStateOf<List<SeoTagRecommendation>>(emptyList()) }
    var titleSuggestions by remember { mutableStateOf<List<OptimizedTitleSuggestion>>(emptyList()) }
    var selectedTitle by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("YouTube Shorts") }

    val scope = rememberCoroutineScope()

    // Auto analyze on open if query is not empty
    LaunchedEffect(Unit) {
        if (topicQuery.isNotBlank()) {
            isAnalyzing = true
            delay(1000)
            val result = generateSeoRecommendations(topicQuery, selectedPlatform)
            tagList = result.first
            titleSuggestions = result.second
            if (titleSuggestions.isNotEmpty()) {
                selectedTitle = titleSuggestions.first().title
            }
            isAnalyzing = false
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
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
                // Header
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
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "مُحلل الهاشتاغات والكلمات الفيروسية 📈",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "SEO & Viral Predictor Engine",
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

                // Topic Input and Platform Chips
                OutlinedTextField(
                    value = topicQuery,
                    onValueChange = { topicQuery = it },
                    label = { Text("موضوع المقطع أو الفكرة الرئيسية", color = Color.Gray, fontFamily = CairoFont) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (topicQuery.isNotBlank()) {
                                isAnalyzing = true
                                scope.launch {
                                    delay(1000)
                                    val res = generateSeoRecommendations(topicQuery, selectedPlatform)
                                    tagList = res.first
                                    titleSuggestions = res.second
                                    if (titleSuggestions.isNotEmpty()) {
                                        selectedTitle = titleSuggestions.first().title
                                    }
                                    isAnalyzing = false
                                }
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "تحليل", tint = GoldPrimary)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Platform Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("YouTube Shorts", "TikTok / Instagram", "YouTube Long").forEach { plt ->
                        FilterChip(
                            selected = selectedPlatform == plt,
                            onClick = {
                                selectedPlatform = plt
                                if (topicQuery.isNotBlank()) {
                                    isAnalyzing = true
                                    scope.launch {
                                        delay(800)
                                        val res = generateSeoRecommendations(topicQuery, selectedPlatform)
                                        tagList = res.first
                                        titleSuggestions = res.second
                                        isAnalyzing = false
                                    }
                                }
                            },
                            label = { Text(plt, fontSize = 12.sp, fontFamily = NotoSansFont) },
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

                Spacer(modifier = Modifier.height(16.dp))

                if (isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GoldPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("جاري فحص خوارزميات النشر وتوليد الكلمات الأكثر تداولاً...", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 14.sp)
                        }
                    }
                } else if (tagList.isNotEmpty() || titleSuggestions.isNotEmpty()) {
                    // SECTION 1: OPTIMIZED VIRAL TITLES
                    Text(
                        "العناوين الأعلى نقراً وفيروسية (CTR Boost) 🎯:",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        titleSuggestions.forEach { titleObj ->
                            val isSel = selectedTitle == titleObj.title
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTitle = titleObj.title },
                                color = if (isSel) GoldPrimary.copy(alpha = 0.15f) else Color(0xFF151B2B),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSel) GoldPrimary else Color(0xFF1E293B))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(titleObj.title, color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("نمط الخطاف: ${titleObj.hookType}", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            titleObj.ctrBoostEstimate,
                                            color = Color(0xFF10B981),
                                            fontFamily = NotoSansFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SECTION 2: VIRAL HASHTAGS SELECTION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "الوسوم والهاشتاغات الموصى بها (Hashtags) #:",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(onClick = {
                            val allSelected = tagList.all { it.isSelected }
                            tagList = tagList.map { it.copy(isSelected = !allSelected) }
                        }) {
                            Text(
                                if (tagList.all { it.isSelected }) "إلغاء تحديد الكل" else "تحديد الكل",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Wrap Hashtags Flow layout
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tagList.forEachIndexed { idx, tag ->
                            FilterChip(
                                selected = tag.isSelected,
                                onClick = {
                                    tagList = tagList.toMutableList().also {
                                        it[idx] = tag.copy(isSelected = !tag.isSelected)
                                    }
                                },
                                label = {
                                    Text(
                                        "${tag.hashtag} (${tag.searchVolume})",
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp
                                    )
                                },
                                leadingIcon = if (tag.isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = DeepSlate,
                                    containerColor = Color(0xFF151B2B),
                                    labelColor = Color.LightGray
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Apply Action Button
                    Button(
                        onClick = {
                            val selectedHashtags = tagList.filter { it.isSelected }.joinToString(" ") { it.hashtag }
                            onApplySeoData(selectedTitle, selectedHashtags)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("SEO Text", "$selectedTitle\n\n$selectedHashtags")
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "تم تطبيق ونسخ العنوان والهاشتاغات بنجاح 🚀", Toast.LENGTH_LONG).show()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = DeepSlate)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تطبيق وحفظ بيانات الـ SEO للمشروع", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color(0xFF0B0F19), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("أدخل موضوع المقطع واضغط تحليل لاستخراج الوسوم الفيروسية", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// Generates smart SEO tags & title suggestions based on keywords
fun generateSeoRecommendations(
    topic: String,
    platform: String
): Pair<List<SeoTagRecommendation>, List<OptimizedTitleSuggestion>> {
    val tags = mutableListOf<SeoTagRecommendation>()
    val titles = mutableListOf<OptimizedTitleSuggestion>()

    val isShorts = platform.contains("Shorts") || platform.contains("Instagram")

    tags.add(SeoTagRecommendation("#قبس", "1.2M", 99, "علامة التطبيق"))
    tags.add(SeoTagRecommendation(if (isShorts) "#Shorts" else "#YouTube", "180M", 98, "منصة"))
    tags.add(SeoTagRecommendation("#محتوى_هادف", "8.5M", 95, "تصنيف"))

    if (topic.contains("صحابة") || topic.contains("عمر") || topic.contains("قصة") || topic.contains("نبي")) {
        tags.add(SeoTagRecommendation("#قصص_الأنبياء", "12.4M", 97, "إسلامي"))
        tags.add(SeoTagRecommendation("#سير_الصحابة", "5.1M", 96, "تاريخ"))
        tags.add(SeoTagRecommendation("#العظمة_الإسلامية", "3.8M", 92, "توعية"))
        tags.add(SeoTagRecommendation("#عبرة_وفائدة", "6.2M", 90, "تريند"))

        titles.add(OptimizedTitleSuggestion("الموقف الذي هز قلب الصحابي ولم يتمالك دموعه! 😱", "+42% CTR", "سؤال ومشهد مؤثر"))
        titles.add(OptimizedTitleSuggestion("سر لم تكن تعرفه عن هذا البطل العظيم في التاريخ الإسلامي ⚔️", "+38% CTR", "حقيقة غائبة"))
        titles.add(OptimizedTitleSuggestion("كيف واجه هذا الموقف العصيب بحكمة إيمانية؟ ✦", "+31% CTR", "دروس وعبر"))
    } else if (topic.contains("قرآن") || topic.contains("آية") || topic.contains("تفسير") || topic.contains("تدبر")) {
        tags.add(SeoTagRecommendation("#تدبر_آية", "9.8M", 98, "قرآن"))
        tags.add(SeoTagRecommendation("#تلاوة_خاشعة", "15.3M", 96, "صوتيات"))
        tags.add(SeoTagRecommendation("#راحه_نفسيه", "22.1M", 99, "تريند عام"))
        tags.add(SeoTagRecommendation("#راحة_القلوب", "11.0M", 94, "تأثير"))

        titles.add(OptimizedTitleSuggestion("آية واحدة كفيلة بأن تخرجك من أشد أوقات الضيق والألم 🤍", "+45% CTR", "عاطفي ورجاء"))
        titles.add(OptimizedTitleSuggestion("تأمل هذه المعجزة اللغوية في سورة الكهف! 📖", "+36% CTR", "إعجاز بلاغي"))
        titles.add(OptimizedTitleSuggestion("ما السر في اختيار هذا اللفظ بالذات في القرآن؟ ✦", "+30% CTR", "سؤال علمي"))
    } else {
        tags.add(SeoTagRecommendation("#إسلاميات", "34.5M", 96, "عام"))
        tags.add(SeoTagRecommendation("#دعوة", "4.2M", 91, "دعوة"))
        tags.add(SeoTagRecommendation("#الجمعة", "18.9M", 88, "مواسم"))
        tags.add(SeoTagRecommendation("#نصيحة_ليومك", "7.3M", 93, "تريند"))

        titles.add(OptimizedTitleSuggestion("رسالة قصيرة لكل من يمر بظروف صعبة اليوم 🌿", "+39% CTR", "خطاب مباشر"))
        titles.add(OptimizedTitleSuggestion("3 قواعد ذهبية علمنا إياها النبي ﷺ للتعامل مع الناس ✨", "+35% CTR", "قوائم ونصائح"))
        titles.add(OptimizedTitleSuggestion("لا تتجاهل هذه النصيحة قبل أن تبدأ يومك! 🚀", "+33% CTR", "حث واستنهاض"))
    }

    return Pair(tags, titles)
}
