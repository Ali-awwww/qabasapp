package com.qabas.app.ui.input

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.qabas.app.ProductionPipelineTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.AppServices
import com.qabas.app.ClonedStylePreset
import com.qabas.app.ProjectState
import com.qabas.app.Translator
import com.qabas.app.TrendingIdea
import com.qabas.app.ContentFilterService
import com.qabas.app.ContentInspectionResult
import com.qabas.app.CloudServices
import com.qabas.app.GuardVerdict
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun IdeaInputSection(
    state: ProjectState,
    onStateChange: (ProjectState) -> Unit,
    onProceed: () -> Unit,
    onChangeSource: () -> Unit,
    onStartVoiceRecording: () -> Unit,
    trendingIdeas: List<TrendingIdea>?,
    isTrendingLoading: Boolean,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    val goldGradient = Brush.horizontalGradient(colors = listOf(GoldSecondary, GoldPrimary))
    val styleButtonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFD4AF37), Color(0xFFF5D76E), Color(0xFFD4AF37))
    )

    var isEnhancingIdea by remember { mutableStateOf(false) }
    var showGuardDialog by remember { mutableStateOf(false) }
    var guardInspectionResult by remember { mutableStateOf<ContentInspectionResult?>(null) }
    
    // Premium Animations
    val infiniteTransition = rememberInfiniteTransition()
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val buttonScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Source Type Indicator / Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151B2B), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    Translator.tr("المدخل المختار: فكرة نصية ✍️"),
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            TextButton(
                onClick = { onChangeSource() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    Translator.tr("تغيير ↩️"),
                    color = TextSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp
                )
            }
        }

        // 1. CORE PROMPT & IDEA FIELD CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = GoldPrimary.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF182232)),
            border = BorderStroke(
                1.5.dp,
                if (state.inputText.isNotEmpty()) GoldPrimary else Color(0xFF1E293B)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            Translator.tr("اكتب فكرة المشهد أو النص ✍️"),
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    if (state.inputText.isNotEmpty()) {
                        TextButton(onClick = { onStateChange(state.copy(inputText = "")) }) {
                            Text(Translator.tr("مسح"), color = Color(0xFFEF4444), fontSize = 12.sp, fontFamily = NotoSansFont)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = { newVal -> onStateChange(state.copy(inputText = newVal)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp, max = 220.dp),
                    placeholder = {
                        Text(
                            Translator.tr("مثال: شرح مبسط وعميق لفضل تدبر القرآن الكريم في حسم القرارات، بأسلوب سينمائي حماسي مع مشاهد طبيعية هادئة..."),
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontFamily = NotoSansFont,
                            fontSize = 13.sp,
                            lineHeight = 22.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0B0F19),
                        unfocusedContainerColor = Color(0xFF0B0F19),
                        focusedIndicatorColor = GoldPrimary,
                        unfocusedIndicatorColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = GoldPrimary
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        fontFamily = NotoSansFont,
                        lineHeight = 24.sp
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Assist Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onStartVoiceRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary.copy(alpha = 0.15f), contentColor = GoldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Translator.tr("صوت 🎤"), fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val holySnippet = "« إِنَّ اللَّهَ وَمَلَائِكَتَهُ يُصَلُّونَ عَلَى النَّبِيِّ ۚ يَا أَيُّهَا الَّذِينَ آمَنُوا صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا »"
                            onStateChange(
                                state.copy(
                                    inputText = if (state.inputText.isBlank()) holySnippet else "${state.inputText}\n$holySnippet"
                                )
                            )
                        },
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Translator.tr("نص شرعي 📖"), color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Magic Prompt Engineer Button
                Button(
                    onClick = {
                        if (state.inputText.isNotBlank() && !isEnhancingIdea) {
                            isEnhancingIdea = true
                            scope.launch {
                                kotlinx.coroutines.delay(1500)
                                val enhanced = """
                                    |[الهدف]: إنتاج فيديو دعوي/إسلامي قصير احترافي.
                                    |[الجمهور المستهدف]: الشباب المسلم على شبكات التواصل.
                                    |[النبرة]: ملهمة، سينمائية، ومؤثرة.
                                    |[الفكرة الأساسية]: ${state.inputText.take(150)}${if (state.inputText.length > 150) "..." else ""}
                                    |
                                    |[الهيكلة المقترحة]:
                                    |1. الخطاف (الثواني 0-3): مشهد يشد الانتباه مع سؤال مثير للتفكير.
                                    |2. الجسد (القصة/المعنى): طرح القضية مع الاستشهاد بآية أو حديث بخلفية هادئة.
                                    |3. الخاتمة (الدعوة للإجراء CTA): رسالة ختامية تترك أثراً مع دعوة للمشاركة.
                                    |
                                    |[التوجيهات المرئية]: ألوان داكنة مع تباين ذهبي، انتقالات ناعمة، ونصوص واضحة.
                                """.trimMargin()
                                
                                onStateChange(state.copy(inputText = enhanced))
                                isEnhancingIdea = false
                                snackbarHostState.showSnackbar(Translator.tr("تمت هندسة الفكرة لتصبح برومبت احترافي! ✨"))
                            }
                        } else if (state.inputText.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar(Translator.tr("يرجى كتابة الفكرة أولاً!")) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                        .shadow(
                            if (state.inputText.isNotBlank() && !isEnhancingIdea) 8.dp * glowPulse else 0.dp,
                            RoundedCornerShape(12.dp),
                            spotColor = Color(0xFF8B5CF6)
                        ),
                    enabled = !isEnhancingIdea
                ) {
                    if (isEnhancingIdea) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Translator.tr("جاري هندسة البرومبت ذكياً..."), color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Translator.tr("الصياغة الهندسية الذكية للبرومبت 🪄"), color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // 2. VIDEO PARAMETERS CARD (المدة، النسبة، الجمهور المستهدف، النبرة)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151B2B)),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Parameters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        Translator.tr("إعدادات الفيديو الأساسية ⚙️"),
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // 1. Aspect Ratio (الأبعاد: طول / عرض)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        Translator.tr("أبعاد الفيديو (الارتفاع والعرض):"),
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "9:16" to "طولي (رأسي)",
                            "16:9" to "عرضي (YouTube)",
                            "1:1" to "مربع (Feed)"
                        ).forEach { (ratio, label) ->
                            val isSelected = state.selectedRatio == ratio
                            Surface(
                                color = if (isSelected) GoldPrimary else Color(0xFF0B0F19),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onStateChange(state.copy(selectedRatio = ratio)) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        ratio,
                                        color = if (isSelected) DeepSlate else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CairoFont
                                    )
                                    Text(
                                        label,
                                        color = if (isSelected) DeepSlate.copy(alpha = 0.8f) else TextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = NotoSansFont,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Video Duration (طول الفيديو)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        Translator.tr("طول الفيديو:"),
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "15 ثانية",
                            "30 ثانية",
                            "60 ثانية",
                            "90 ثانية"
                        ).forEach { dur ->
                            val isSelected = state.videoDuration == dur
                            Surface(
                                color = if (isSelected) GoldPrimary else Color(0xFF0B0F19),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onStateChange(state.copy(videoDuration = dur)) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dur,
                                        color = if (isSelected) DeepSlate else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CairoFont
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Target Audience (موجه لمن)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        Translator.tr("الجمهور المستهدف (موجه لمن):"),
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "الشباب ورواد التواصل 📱",
                            "عامة المسلمين 🌍",
                            "طلاب العلم والمهتمين 📚",
                            "غير المسلمين والتعريف بالإسلام 🕊️",
                            "الناشئة والأطفال 🌟"
                        ).forEach { audience ->
                            val isSelected = state.marketingGoal.contains(audience.take(6)) || state.marketingGoal == audience
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFF1E1B4B) else Color(0xFF0B0F19))
                                    .border(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                    .clickable { onStateChange(state.copy(marketingGoal = audience)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    audience,
                                    color = if (isSelected) GoldPrimary else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = NotoSansFont
                                )
                            }
                        }
                    }
                }

                // 4. Tone of Content (نبرة المحتوى)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        Translator.tr("نبرة الطرح:"),
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Translator.tr("خاشع وهادئ 🌿"),
                            Translator.tr("ملحمي وحماسي ⚡"),
                            Translator.tr("تأملي عميق 🌌"),
                            Translator.tr("سرد وثائقي 🎙️")
                        ).forEach { tone ->
                            val cleanToneName = tone.split(" ")[0]
                            val isSelected = state.contentTone.contains(cleanToneName)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFF2C1E12) else Color(0xFF0B0F19))
                                    .border(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                    .clickable { onStateChange(state.copy(contentTone = cleanToneName)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    tone,
                                    color = if (isSelected) GoldPrimary else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = NotoSansFont
                                )
                            }
                        }
                    }
                }
            }
        }

        // QUICK INSPIRATION CAROUSEL
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                Translator.tr("أفكار مقترحة للانطلاق السريع:"),
                color = TextSecondary,
                fontFamily = CairoFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            if (isTrendingLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(20.dp))
                }
            } else {
                val displayIdeas = trendingIdeas ?: emptyList()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    displayIdeas.take(6).forEach { idea ->
                        Card(
                            modifier = Modifier
                                .width(200.dp)
                                .clickable {
                                    onStateChange(state.copy(inputText = idea.title + "\n" + idea.description))
                                    scope.launch { snackbarHostState.showSnackbar(Translator.tr("تم تمكين الفكرة!")) }
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF182232)),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(idea.title, color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                Text(idea.description, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, maxLines = 2, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }

        // FLOATING GOLDEN ACTION BUTTON
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = {
                    val text = state.inputText.trim()
                    if (text.isEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                Translator.tr("اكتب أو تحدث فكرتك أولاً للانتقال للإخراج!")
                            )
                        }
                        return@Button
                    }

                    val inspection = ContentFilterService.quickInspect(text)

                    scope.launch {
                        try {
                            CloudServices.Database.recordContentGuardCheck(
                                text = text,
                                verdict = inspection.verdict.name,
                                score = inspection.score,
                                reason = inspection.reason
                            )
                        } catch (_: Exception) {
                        }
                        try {
                            AppServices.getAnalyticsService(context).logEvent(
                                "script_guard_check",
                                mapOf(
                                    "verdict" to inspection.verdict.name,
                                    "score" to inspection.score
                                )
                            )
                        } catch (_: Exception) {
                        }
                    }

                    if (inspection.verdict == GuardVerdict.REJECTED) {
                        guardInspectionResult = inspection
                        showGuardDialog = true
                        ProductionPipelineTracker.record(context, ProductionPipelineTracker.Stage.CONTENT_GUARD, ProductionPipelineTracker.Result.FAILURE, "رفض المحتوى: ${inspection.verdict}", "السبب: ${inspection.reason}", 0)
                    } else {
                        ProductionPipelineTracker.record(context, ProductionPipelineTracker.Stage.CONTENT_GUARD, ProductionPipelineTracker.Result.SUCCESS, "فحص المحتوى مقبول", "النقطة: ${inspection.score}", 0)
                        ProductionPipelineTracker.record(context, ProductionPipelineTracker.Stage.IDEA_INPUT, ProductionPipelineTracker.Result.SUCCESS, "فكرة جديدة: ${text.take(80)}", "النبرة: ${state.contentTone.ifBlank { "افتراضي" }}", 0)
                        onProceed()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .scale(if (state.inputText.isNotBlank()) buttonScale else 1f)
                    .shadow(
                        if (state.inputText.isNotBlank()) 16.dp * glowPulse else 0.dp,
                        RoundedCornerShape(16.dp),
                        spotColor = GoldPrimary
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (state.inputText.isNotBlank()) {
                                Brush.horizontalGradient(colors = listOf(GoldSecondary, GoldPrimary, GoldSecondary))
                            } else {
                                androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B))
                            },
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.videoStyleAnalysis != null) {
                                val activeName = state.videoStyleAnalysis?.detectedStyle
                                    ?.ifEmpty { state.editingStyle } ?: ""
                                Translator.tr("توليد الفيديو بـ (") + activeName + Translator.tr(") 🚀✨")
                            } else {
                                Translator.tr("تحويل الفكرة إلى مشهد سينمائي 🚀")
                            },
                            color = if (state.inputText.isNotBlank()) DeepSlate else TextSecondary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (state.inputText.isNotBlank()) DeepSlate else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // CONTENT GUARD INSPECTION DIALOG
        if (showGuardDialog && guardInspectionResult != null) {
            val guardRes = guardInspectionResult!!
            val isRejected = guardRes.verdict == GuardVerdict.REJECTED
            val bannerColor = if (isRejected) Color(0xFFEF4444) else Color(0xFFF59E0B)

            AlertDialog(
                onDismissRequest = { showGuardDialog = false },
                title = {
                    Text(
                        text = if (isRejected) "محتوى مرفوض" else "مراجعة مقترحة",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = guardRes.reason,
                            color = Color(0xFFE2E8F0),
                            fontFamily = NotoSansFont,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        if (guardRes.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            guardRes.warnings.forEach { w ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.FiberManualRecord,
                                        contentDescription = null,
                                        tint = bannerColor,
                                        modifier = Modifier.size(8.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        w,
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (!isRejected && !guardRes.improvedScript.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "الصياغة المقترحة البديلة:",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0B0F19), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    guardRes.improvedScript!!,
                                    color = Color.White,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (isRejected) {
                        Button(
                            onClick = { showGuardDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "تعديل النص ✍️",
                                color = Color.White,
                                fontFamily = NotoSansFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!guardRes.improvedScript.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        onStateChange(state.copy(inputText = guardRes.improvedScript!!))
                                        showGuardDialog = false
                                        onProceed()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = DeepSlate,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "اعتماد الصياغة المصوبة والمتابعة ✨",
                                        color = DeepSlate,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showGuardDialog = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Text(
                                        "تعديل ✍️",
                                        color = Color.White,
                                        fontFamily = NotoSansFont,
                                        fontSize = 12.sp
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        showGuardDialog = false
                                        onProceed()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "المتابعة على أي حال",
                                        color = TextSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                },
                dismissButton = null,
                containerColor = Color(0xFF151B2B)
            )
        }
    } // Box
}
