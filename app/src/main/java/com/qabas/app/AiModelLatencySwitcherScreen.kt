package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * ⚡ شاشة توجيه وتبديل نماذج الذكاء الاصطناعي الذكية حسب سرعة الاستجابة الحية (Latency Benchmark & Model Router)
 *
 * تسمح للمطور بـ:
 * 1. قياس سرعة استجابة (Latency) الخوادم الفعلية في اللحظة الحالية لـ Gemini و Groq و OpenAI.
 * 2. التبديل التلقائي للمحرك الأسرع لحظياً أو تثبيت محرك يدوي بضغطة زر.
 * 3. تشغيل سباق ومقارنة حية (Head-to-Head Race) بين النماذج لنفس الفكرة ورؤية فارق الميلي ثانية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelLatencySwitcherScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val scope = rememberCoroutineScope()

    val activeEngine by AiModelLatencyRouter.activeEngine.collectAsState()
    val isAutoFastest by AiModelLatencyRouter.isAutoFastest.collectAsState()
    val isBenchmarking by AiModelLatencyRouter.isBenchmarking.collectAsState()
    val benchmarkResults by AiModelLatencyRouter.benchmarkResults.collectAsState()
    val lastBenchmarkTime by AiModelLatencyRouter.lastBenchmarkTime.collectAsState()

    var testPrompt by remember { mutableStateOf("اقترح 3 خطافات دعوية لافتة عن بر الوالدين") }
    var comparisonResult by remember { mutableStateOf<AiModelLatencyRouter.HeadToHeadComparison?>(null) }
    var isComparing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AiModelLatencyRouter.init(context)
        if (benchmarkResults.isEmpty()) {
            AiModelLatencyRouter.runFullBenchmark(context)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        containerColor = DeepSlate,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PresetPrimary.copy(alpha = 0.15f))
                                .border(1.dp, PresetPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "توجيه وتبديل النماذج الذكي",
                                color = PresetPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "تبديل المحركات حسب سرعة الاستجابة الحية (Latency)",
                                color = TextSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PresetPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { AiModelLatencyRouter.runFullBenchmark(context) } },
                        enabled = !isBenchmarking
                    ) {
                        if (isBenchmarking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PresetPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث الفحص", tint = PresetPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepSlate),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. بطاقة المحرك النشط الفضائية (Spatial HUD Active Engine Card)
            item {
                ActiveEngineHeroCard(
                    activeEngine = activeEngine,
                    isAutoFastest = isAutoFastest,
                    pulseAlpha = pulseAlpha,
                    onToggleAuto = { AiModelLatencyRouter.setAutoFastest(context, it) }
                )
            }

            // 2. زر إطلاق الفحص الشامل للسرعة
            item {
                Button(
                    onClick = { scope.launch { AiModelLatencyRouter.runFullBenchmark(context) } },
                    enabled = !isBenchmarking,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.secondarySurface,
                        contentColor = PresetPrimary
                    ),
                    border = BorderStroke(1.2.dp, PresetPrimary.copy(alpha = 0.6f))
                ) {
                    if (isBenchmarking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PresetPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري قياس استجابة النماذج لحظياً...", fontFamily = CairoFont, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فحص سرعة استجابة جميع النماذج الآن ⚡", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // 3. عنوان قائمة النماذج المتاحة وسرعاتها
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "النماذج السحابية وسرعة الاستجابة الحية",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (lastBenchmarkTime > 0) {
                        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastBenchmarkTime))
                        Text(
                            "آخر فحص: $timeStr",
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 4. بطاقات النماذج الأربعة (Groq, Gemini, OpenAI, Pollinations)
            val modelsList = listOf(
                ModelUiConfig(
                    id = "Groq",
                    title = "Groq LPU (Ultra Fast)",
                    spec = "Llama 3.3 70B Versatile",
                    icon = Icons.Default.FlashOn,
                    accentColor = theme.success,
                    description = "معالجة فائقة السرعة على رقاقات LPU المخصصة، الأفضل للمهام الفورية والخطافات."
                ),
                ModelUiConfig(
                    id = "Gemini",
                    title = "Google Gemini",
                    spec = "Gemini 2.5 Flash / 2.0 Flash",
                    icon = Icons.Default.AutoAwesome,
                    accentColor = theme.primary,
                    description = "سياق فائق الضخامة، فهم لغوي عالي الدقة وتنسيق المشاهد السينمائية المعقدة."
                ),
                ModelUiConfig(
                    id = "OpenAI",
                    title = "OpenAI Platform",
                    spec = "GPT-4o Mini",
                    icon = Icons.Default.Psychology,
                    accentColor = theme.accent,
                    description = "محرك متزن جداً في التفكير الإبداعي والمنطقي والدعم اللغوي العام."
                ),
                ModelUiConfig(
                    id = "Pollinations",
                    title = "Pollinations AI (Free)",
                    spec = "Mistral Large / Free Engine",
                    icon = Icons.Default.Public,
                    accentColor = theme.warning,
                    description = "يعمل تلقائياً بدون أي مفتاح API أو اشتراك كشبكة أمان مجانية."
                )
            )

            items(modelsList) { modelConfig ->
                val result = benchmarkResults[modelConfig.id]
                val isActive = activeEngine.equals(modelConfig.id, ignoreCase = true)

                ModelBenchmarkCard(
                    config = modelConfig,
                    result = result,
                    isActive = isActive,
                    onSelectManual = {
                        AiModelLatencyRouter.setManualEngine(context, modelConfig.id)
                    }
                )
            }

            // 5. ميدان المقارنة المباشرة (Head-to-Head Live Race: Gemini vs Groq)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                HeadToHeadRaceSection(
                    testPrompt = testPrompt,
                    onPromptChange = { testPrompt = it },
                    isComparing = isComparing,
                    comparisonResult = comparisonResult,
                    onStartRace = {
                        scope.launch {
                            isComparing = true
                            comparisonResult = AiModelLatencyRouter.runLiveHeadToHead(testPrompt)
                            isComparing = false
                        }
                    }
                )
            }
        }
    }
}

data class ModelUiConfig(
    val id: String,
    val title: String,
    val spec: String,
    val icon: ImageVector,
    val accentColor: Color,
    val description: String
)

@Composable
private fun ActiveEngineHeroCard(
    activeEngine: String,
    isAutoFastest: Boolean,
    pulseAlpha: Float,
    onToggleAuto: (Boolean) -> Unit
) {
    val theme = QabasTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(
                listOf(
                    PresetPrimary.copy(alpha = 0.8f),
                    theme.success.copy(alpha = 0.6f),
                    PresetAccent.copy(alpha = 0.4f)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            theme.cardSurface,
                            DeepSlate,
                            theme.background
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(theme.success.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "المحرك المعتمد للعمليات حالياً",
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.5.sp
                        )
                    }

                    // شارة التوجيه التلقائي
                    Surface(
                        color = if (isAutoFastest) theme.success.copy(alpha = 0.15f) else theme.accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isAutoFastest) theme.success.copy(alpha = 0.5f) else theme.accent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isAutoFastest) "⚡ توجيه تلقائي للأسرع" else "🔒 مثبت يدوياً",
                            color = if (isAutoFastest) theme.success else theme.accent,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = activeEngine,
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Text(
                            text = when (activeEngine) {
                                "Groq" -> "محرك LPU فائق السرعة • Llama 3.3 70B"
                                "Gemini" -> "محرك Google متعدد الوسائط • Gemini 2.5 Flash"
                                "OpenAI" -> "محرك GPT-4o Mini المتزن"
                                else -> "المحرك المجاني بلا مفتاح"
                            },
                            color = theme.textPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PresetPrimary.copy(alpha = 0.18f))
                            .border(1.2.dp, PresetPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (activeEngine) {
                                "Groq" -> Icons.Default.Bolt
                                "Gemini" -> Icons.Default.AutoAwesome
                                "OpenAI" -> Icons.Default.Psychology
                                else -> Icons.Default.Public
                            },
                            contentDescription = null,
                            tint = PresetPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = theme.secondarySurface, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // تبديل الوضع التلقائي الذكي
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "التوجيه الذكي التلقائي (Auto-Fastest Routing)",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            "توجيه مهام توليد النصوص تلقائياً لأسرع محرك استجاب في الفحص اللحظي.",
                            color = TextSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 10.5.sp
                        )
                    }

                    Switch(
                        checked = isAutoFastest,
                        onCheckedChange = onToggleAuto,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PresetPrimary,
                            checkedTrackColor = PresetPrimary.copy(alpha = 0.35f),
                            uncheckedThumbColor = theme.textSecondary,
                            uncheckedTrackColor = theme.secondarySurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelBenchmarkCard(
    config: ModelUiConfig,
    result: ModelBenchmarkResult?,
    isActive: Boolean,
    onSelectManual: () -> Unit
) {
    val theme = QabasTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            if (isActive) config.accentColor.copy(alpha = 0.85f) else theme.secondarySurface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(config.accentColor.copy(alpha = 0.15f))
                            .border(1.dp, config.accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(config.icon, contentDescription = null, tint = config.accentColor, modifier = Modifier.size(22.dp))
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                config.title,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (isActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = config.accentColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.8.dp, config.accentColor.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        "النشط حالياً ⭐",
                                        color = config.accentColor,
                                        fontFamily = CairoFont,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(config.spec, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                    }
                }

                // عرض سرعة الاستجابة الحية (Latency Badge)
                val latency = result?.latencyMs ?: -1L
                val isSuccess = result?.isSuccess == true
                Surface(
                    color = when {
                        !isSuccess -> theme.destructive.copy(alpha = 0.15f)
                        latency in 1..400 -> theme.success.copy(alpha = 0.2f)
                        latency in 401..900 -> theme.primary.copy(alpha = 0.2f)
                        else -> theme.warning.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            !isSuccess -> theme.destructive.copy(alpha = 0.5f)
                            latency in 1..400 -> theme.success.copy(alpha = 0.6f)
                            latency in 401..900 -> theme.primary.copy(alpha = 0.6f)
                            else -> theme.warning.copy(alpha = 0.6f)
                        }
                    )
                ) {
                    Text(
                        text = when {
                            result == null -> "لم يُفحص بعد ⏱️"
                            !isSuccess -> "غير متصل ❌"
                            else -> "${latency}ms ⚡"
                        },
                        color = when {
                            !isSuccess -> theme.destructive
                            latency in 1..400 -> theme.success
                            latency in 401..900 -> theme.primary
                            else -> theme.warning
                        },
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // شريط قياس السرعة النسبي (Visual Speed Meter)
            val latency = result?.latencyMs ?: 0L
            if (result != null && result.isSuccess && latency > 0) {
                val fraction = (latency / 1200f).coerceIn(0.08f, 1f)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("مؤشر وقت الاستجابة", color = TextSecondary, fontSize = 10.sp, fontFamily = NotoSansFont)
                        Text(
                            when {
                                latency < 350 -> "سرعة خارقة جداً (LPU Instant) 🚀"
                                latency < 700 -> "سرعة ممتازة ⚡"
                                else -> "سرعة متوسطة ⏳"
                            },
                            color = config.accentColor,
                            fontSize = 10.sp,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(theme.secondarySurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(config.accentColor, config.accentColor.copy(alpha = 0.5f))
                                    )
                                )
                        )
                    }
                }
            } else if (result != null && !result.isSuccess) {
                Text(
                    text = result.message,
                    color = theme.destructive,
                    fontSize = 11.sp,
                    fontFamily = CairoFont
                )
            } else {
                Text(
                    text = config.description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = NotoSansFont,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // زر التعيين اليدوي الفوري
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onSelectManual,
                    enabled = !isActive && (result == null || result.isSuccess),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isActive) theme.textTertiary else config.accentColor
                    ),
                    border = BorderStroke(1.dp, if (isActive) theme.separator else config.accentColor.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isActive) "المحرك المعتمد حالياً" else "تبديل وتعيين رئيسي ⚡",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadToHeadRaceSection(
    testPrompt: String,
    onPromptChange: (String) -> Unit,
    isComparing: Boolean,
    comparisonResult: AiModelLatencyRouter.HeadToHeadComparison?,
    onStartRace: () -> Unit
) {
    val theme = QabasTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(
                listOf(
                    theme.success.copy(alpha = 0.5f),
                    PresetPrimary.copy(alpha = 0.5f),
                    theme.primary.copy(alpha = 0.5f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PresetPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SportsScore, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        "ميدان سباق السرعة المباشر (Groq vs Gemini) 🏁",
                        color = PresetPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "اختبار حي يقارن سرعة الاستجابة الفعلية لنفس الفكرة بالميلي ثانية",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = testPrompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("فكرة الاختبار", fontFamily = CairoFont, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PresetPrimary,
                    unfocusedBorderColor = theme.separator,
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onStartRace,
                enabled = !isComparing && testPrompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary, contentColor = theme.background)
            ) {
                if (isComparing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = theme.background, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري تشغيل سباق النماذج بالمللي ثانية...", fontFamily = CairoFont, fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إطلاق سباق السرعة الآن 🚀", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // نتائج السباق إن وجدت
            if (comparisonResult != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = theme.secondarySurface, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // إعلان الفائز
                Surface(
                    color = theme.success.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, theme.success.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏆 الفائز بالسرعة:", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(comparisonResult.winner, color = theme.success, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        if (comparisonResult.timeSavedMs > 0) {
                            Text(
                                "فارق: ${comparisonResult.timeSavedMs}ms أسرع!",
                                color = PresetPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // كارت مقارنة الجانبين
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Groq Result Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.cardSurface),
                        border = BorderStroke(1.dp, theme.success.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Groq LPU", color = theme.success, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${comparisonResult.groqLatencyMs}ms", color = theme.success, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = comparisonResult.groqOutput.take(120),
                                color = TextSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Gemini Result Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.cardSurface),
                        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Gemini", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${comparisonResult.geminiLatencyMs}ms", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = comparisonResult.geminiOutput.take(120),
                                color = TextSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
