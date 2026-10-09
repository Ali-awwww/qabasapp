package com.qabas.app

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

data class ActiveTrendAlert(
    val id: String,
    val title: String,
    val threatLevel: String, // حرج، مرتفع، متوسط
    val category: String,
    val trendSummary: String,
    val coreArgument: String,
    val suggestedHook: String,
    val evidenceText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentGuardScreen(
    onBack: () -> Unit,
    onStartScriptWithText: (String) -> Unit,
    onOpenTeleprompterWithText: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "🔍 فاحص النصوص والسكريبتات",
        "🚨 مرصد الثغور والتريندات",
        "⚡ تفنيد التريندات بالذكاء الاصطناعي",
        "🏅 وسام المُرابط والأثر"
    )

    // --- Script Inspector State ---
    var inspectInputText by remember {
        mutableStateOf("عن أبي هريرة رضي الله عنه قال: قال رسول الله ﷺ: «من كان يؤمن بالله واليوم الآخر فليقل خيراً أو ليصمت». مقطع قصير يوضح أثر الكلمة الطيبة في بناء القلوب وتجنب الفتن في وسائل التواصل.")
    }
    var inspectionResult by remember { mutableStateOf<ContentInspectionResult?>(null) }
    var isInspecting by remember { mutableStateOf(false) }

    // Run initial inspection once
    LaunchedEffect(Unit) {
        inspectionResult = ContentFilterService.quickInspect(inspectInputText)
    }

    // --- Active Trend Alerts Data ---
    val activeAlerts = remember {
        listOf(
            ActiveTrendAlert(
                id = "alert_1",
                title = "موجة التشكيك في جدوى الاستقامة والالتزام",
                threatLevel = "حرج 🚨",
                category = "تزكية وعقيدة",
                trendSummary = "انتشار فيديوهات تدعي أن الالتزام بالدين يسبب التضييق والاكتئاب، والترويج للانفلات بدعوى الحرية.",
                coreArgument = "السعادة الحقيقية والطمأنينة هي في الاتساق مع فطرة الله {أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ}، وملاحقة الشهوات بلا ضابط تورث الشتات والضياع.",
                suggestedHook = "«يقولون لك الالتزام يسرق شبابك؟ تعال أقول لك حقيقة لا يريدونك أن تفهمها...»",
                evidenceText = "قوله تعالى: {وَمَنْ أَعْرَضَ عَن ذِكْرِي فَإِنَّ لَهُ مَعِيشَةً ضَنكًا}، وحديث «عجباً لأمر المؤمن إن أمره كله خير»."
            ),
            ActiveTrendAlert(
                id = "alert_2",
                title = "تريند السخرية من القيم الأسرية وبر الوالدين",
                threatLevel = "مرتفع ⚡",
                category = "أخلاق واجتماع",
                trendSummary = "مقاطع كوميدية تعزز عقوق الوالدين وتصور الأب والأم كعائق أمام طموح الشاب وسعادته.",
                coreArgument = "الوالدان هم الباب الأوسط للجنة، والعقوق سبب محق البركة والتوفيق في الدنيا قبل الآخرة. النجاح الحقيقي يبدأ من رضاهما.",
                suggestedHook = "«فيديو واحد قد يخسرك أعظم باب للجنة وأنت تضحك! انتبه مما تشاهده...»",
                evidenceText = "قوله تعالى: {وَبِالْوَالِدَيْنِ إِحْسَانًا}، وحديث «رغم أنف ثم رغم أنف من أدرك أبويه عند الكبر أحدهما أو كلاهما فلم يدخل الجنة»."
            ),
            ActiveTrendAlert(
                id = "alert_3",
                title = "موجة اليأس والاحباط من واقع الأمة",
                threatLevel = "متوسط 🟡",
                category = "وعي وأمل",
                trendSummary = "نشر مقاطع تبث العجز وتوحي بانتصار الباطل الدائم وعدم جدوى أي عمل إصلاحي.",
                coreArgument = "المسلم لا يعرف اليأس، وسنة الله متجددة في تداول الأيام. الواجب على كل مسلم غرس غراسه وبذل وسعه دون انتظار النتائج.",
                suggestedHook = "«إن ظننت أن الباطل قد انتصر نهائياً، فأنت لم تقرأ التاريخ ولم تفهم قوله تعالى...»",
                evidenceText = "قوله تعالى: {وَلا تَهِنُوا وَلا تَحْزَنُوا وَأَنْتُمُ الأَعْلَوْنَ إِنْ كُنْتُمْ مُؤْمِنِينَ}، وحديث «إن قامت الساعة وفي يد أحدكم فسيلة فليغرسها»."
            )
        )
    }

    // --- Refutation Engine State ---
    var inputTrendText by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf("رد عقلاني ودعوي هادئ") }
    val tones = listOf("رد عقلاني ودعوي هادئ", "رد حماسي وقوي", "كشف التناقض والبديل الإيجابي")
    
    var isGeneratingRefutation by remember { mutableStateOf(false) }
    var generatedRefutationScript by remember { mutableStateOf("") }

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
                                .background(Brush.horizontalGradient(listOf(Color(0xFFDC2626), GoldPrimary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("مرصد الثغور ومدافعة الفتن", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("قمع الشبهات والتريندات بالحق والجمال 🛡️", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Rank Status Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("رتبتك الحالية: ", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        Text("مُرابط الثغور الرقمية ⚔️", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF151B2B), RoundedCornerShape(8.dp))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("مستوى الأثر: 84%", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tabs Bar
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CardSurface,
                contentColor = GoldPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = GoldPrimary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) GoldPrimary else TextSecondary,
                                fontFamily = CairoFont,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: Script & Text Guard Inspector
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(GoldPrimary.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Security, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("فاحص النصوص والضوابط الشرعية 🛡️", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("فحص فوري للنص قبل التوليد والتصدير لضمان السلامة والجاذبية.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Input Field
                                    OutlinedTextField(
                                        value = inspectInputText,
                                        onValueChange = { 
                                            inspectInputText = it
                                            inspectionResult = ContentFilterService.quickInspect(it)
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                                        placeholder = { Text("الصق هنا فكرة الفيديو، الآية، الحديث، أو السكريبت الكامل لفحصه...", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        label = { Text("نص الفكرة أو السيناريو", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = Color(0xFF1E293B),
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedContainerColor = Color(0xFF141C27),
                                            unfocusedContainerColor = Color(0xFF141C27)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Demo presets
                                    Text("أمثلة سريعة للتجربة:", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                inspectInputText = "مقطع تدبر في قوله تعالى: {أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ}، يوضح أثر المداومة على أذكار الصباح والمساء في نزول السكينة."
                                                inspectionResult = ContentFilterService.quickInspect(inspectInputText)
                                            },
                                            label = { Text("مناسب ✅", fontSize = 11.sp, fontFamily = NotoSansFont) },
                                            colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF151B2B), labelColor = Color(0xFF4CAF50))
                                        )

                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                inspectInputText = "أقسم عليك أن تنشر هذا المقطع لعشرة أشخاص وإلا ستحل عليك مصيبة اليوم! معجزة حقيقية حدثت لشخص بعد قراءة هذا المنشور."
                                                inspectionResult = ContentFilterService.quickInspect(inspectInputText)
                                            },
                                            label = { Text("يحتاج مراجعة ⚠️", fontSize = 11.sp, fontFamily = NotoSansFont) },
                                            colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF151B2B), labelColor = Color(0xFFF59E0B))
                                        )

                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                inspectInputText = "فيديو يحتوي على ألفاظ بذيئة وشتم وسخرية من الأنبياء والدين وتحديات راقصة عارية."
                                                inspectionResult = ContentFilterService.quickInspect(inspectInputText)
                                            },
                                            label = { Text("مرفوض ❌", fontSize = 11.sp, fontFamily = NotoSansFont) },
                                            colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF151B2B), labelColor = Color(0xFFEF4444))
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Deep AI inspect button
                                    Button(
                                        onClick = {
                                            if (inspectInputText.isBlank()) {
                                                Toast.makeText(context, "يرجى كتابة نص للفحص أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isInspecting = true
                                            scope.launch {
                                                val res = ContentFilterService.inspectWithAI(inspectInputText)
                                                inspectionResult = res
                                                isInspecting = false
                                                
                                                // Sync with Cloud & Analytics
                                                CloudServices.Database.recordContentGuardCheck(
                                                    text = inspectInputText,
                                                    verdict = res.verdict.name,
                                                    score = res.score,
                                                    reason = res.reason
                                                )
                                                AppServices.getAnalyticsService(context).logEvent(
                                                    "content_guard_inspection",
                                                    mapOf(
                                                        "verdict" to res.verdict.name,
                                                        "score" to res.score,
                                                        "source" to "guard_hub"
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !isInspecting
                                    ) {
                                        if (isInspecting) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepSlate, strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("جاري الفحص المعمق بحارس المحتوى...", color = DeepSlate, fontFamily = CairoFont)
                                        } else {
                                            Icon(Icons.Default.Verified, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("فحص وتدقيق عميق بالذكاء الاصطناعي ✨", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Inspection Result Card
                        inspectionResult?.let { res ->
                            item {
                                val verdictColor = when (res.verdict) {
                                    GuardVerdict.APPROPRIATE -> Color(0xFF22C55E)
                                    GuardVerdict.NEEDS_REVIEW -> Color(0xFFF59E0B)
                                    GuardVerdict.REJECTED -> Color(0xFFEF4444)
                                }

                                val verdictBg = when (res.verdict) {
                                    GuardVerdict.APPROPRIATE -> Color(0xFF064E3B)
                                    GuardVerdict.NEEDS_REVIEW -> Color(0xFF451A03)
                                    GuardVerdict.REJECTED -> Color(0xFF450A0A)
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                                    shape = RoundedCornerShape(18.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, verdictColor.copy(alpha = 0.8f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        // Header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .background(verdictBg, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        when (res.verdict) {
                                                            GuardVerdict.APPROPRIATE -> Icons.Default.CheckCircle
                                                            GuardVerdict.NEEDS_REVIEW -> Icons.Default.Warning
                                                            GuardVerdict.REJECTED -> Icons.Default.Cancel
                                                        },
                                                        contentDescription = null,
                                                        tint = verdictColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = res.verdict.badge,
                                                        color = verdictColor,
                                                        fontFamily = CairoFont,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp
                                                    )
                                                    Text(
                                                        text = res.title,
                                                        color = Color.White,
                                                        fontFamily = NotoSansFont,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }

                                            // Score Badge
                                            Surface(
                                                color = verdictBg,
                                                shape = RoundedCornerShape(10.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, verdictColor)
                                            ) {
                                                Text(
                                                    "${res.score}%",
                                                    color = verdictColor,
                                                    fontFamily = RobotoMonoFont,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Concise Reason Box
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column {
                                                Text("السبب والنتيجة المباشرة:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(res.reason, color = Color.White, fontFamily = NotoSansFont, fontSize = 13.sp, lineHeight = 18.sp)
                                            }
                                        }

                                        // Warnings
                                        if (res.warnings.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("ملاحظات تدقيقية:", color = Color(0xFFEF4444), fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            res.warnings.forEach { warning ->
                                                Row(
                                                    modifier = Modifier.padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(8.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(warning, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        // Suggestions
                                        if (res.suggestions.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("توصيات التحسين والجاذبية:", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            res.suggestions.forEach { sug ->
                                                Row(
                                                    modifier = Modifier.padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(sug, color = Color(0xFFCBD5E1), fontFamily = NotoSansFont, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        // Improved Script Action
                                        res.improvedScript?.let { improved ->
                                            if (improved.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Text("الصياغة المقترحة المصوبة ✨:", color = Color(0xFF38BDF8), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                                        .padding(12.dp)
                                                ) {
                                                    Text(improved, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 18.sp)
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            clipboardManager.setText(AnnotatedString(improved))
                                                            Toast.makeText(context, "تم نسخ النص المصوب 📋", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Text("نسخ 📋", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                    }

                                                    Button(
                                                        onClick = { onStartScriptWithText(improved) },
                                                        modifier = Modifier.weight(1.4f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Icon(Icons.Default.Movie, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("بدء المونتاج به 🎬", color = DeepSlate, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                1 -> {
                    // TAB 1: Active Wave Alerts
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF450A0A), Color(0xFF0B0F19))))
                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("التنبيه الفوري للثغور الرقمية 🚨", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("تريندات وموجات تحتاج لمدافعة سريعة من صناع المحتوى الإسلامي لتفنيد الباطل.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }

                        items(activeAlerts) { alert ->
                            TrendAlertCard(
                                alert = alert,
                                onGenerateRebutt = {
                                    val fullScript = """
                                        🎬 سيناريو مدافعة ورد على: ${alert.title}
                                        
                                        🔥 الخطاف الجاذب (0-3 ثوان):
                                        ${alert.suggestedHook}
                                        
                                        💡 التفنيد والرد العلمي:
                                        ${alert.coreArgument}
                                        
                                        📖 الدليل الشرعي والأثر:
                                        ${alert.evidenceText}
                                        
                                        ✨ الرسالة الختامية:
                                        «لا تدع الشبهات تعصف بوعيك، تمسك بنور الحق واعتز بهويتك الإسلامية!»
                                    """.trimIndent()
                                    onStartScriptWithText(fullScript)
                                },
                                onOpenTeleprompter = {
                                    val promptText = "${alert.suggestedHook}\n\n${alert.coreArgument}\n\n${alert.evidenceText}"
                                    onOpenTeleprompterWithText(promptText)
                                },
                                onCopyDetails = {
                                    val text = "${alert.title}\nالرد: ${alert.coreArgument}\nالدليل: ${alert.evidenceText}"
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "تم نسخ تفاصيل الرد 📋", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
                2 -> {
                    // TAB 2: AI Trend Refutation Engine
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(26.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("مُفكك التريندات ومولّد الردود القاطعة ✨", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("أدخل اسم التريند الفاسد، أو الشبهة المنتشرة، وسيوم الذكاء الاصطناعي بتفكيكها وصياغة سيناريو رد قاطع وجذاب للجمهور.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 18.sp)

                                    Spacer(modifier = Modifier.height(16.dp))

                                    OutlinedTextField(
                                        value = inputTrendText,
                                        onValueChange = { inputTrendText = it },
                                        modifier = Modifier.fillMaxWidth().height(100.dp),
                                        placeholder = { Text("مثال: تريند السخرية من الحجاب، أو مقطع يروج للإلحاد بدعوى العلم...", color = Color.Gray, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        label = { Text("وصف التريند أو الشبهة", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = Color(0xFF1E293B),
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            focusedContainerColor = Color(0xFF141C27),
                                            unfocusedContainerColor = Color(0xFF141C27)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text("أسلوب الرد والتفنيد:", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(tones) { tone ->
                                            val isSelected = selectedTone == tone
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedTone = tone },
                                                label = { Text(tone, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = GoldPrimary,
                                                    selectedLabelColor = DeepSlate,
                                                    containerColor = Color(0xFF151B2B),
                                                    labelColor = TextPrimary
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            if (inputTrendText.isBlank()) {
                                                Toast.makeText(context, "يرجى كتابة الشبهة أو التريند أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isGeneratingRefutation = true
                                            scope.launch {
                                                try {
                                                    val prompt = """
                                                        أنت خبير في التوعية الإسلامية ومدافعة الشبهات الرقمية.
                                                        قم بصياغة سيناريو فيديو قصير (Reel / Shorts - 60 ثانية) لتفنيد والرد على التريند التالي: "$inputTrendText".
                                                        أسلوب الرد المطلوب: "$selectedTone".
                                                        
                                                        الهيكلية المطردة:
                                                        1) خطاف جاذب (Hook) في أول 3 ثوان بدون تنفير.
                                                        2) تفكيك الفكرة الخاطئة وإظهار تناقضها بأسلوب عقلاني ومقنع.
                                                        3) الدليل الشرعي أو الفطري أو العلمي المؤيد للحق.
                                                        4) البديل الإيجابي ودعوة واضحة للاعتزاز بالدين.
                                                    """.trimIndent()

                                                    val result = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))
                                                    generatedRefutationScript = result
                                                } catch (e: Exception) {
                                                    generatedRefutationScript = """
                                                        🎬 سيناريو تفنيد ومدافعة:
                                                        
                                                        🔥 الخطاف: «سمعت كِذبة انتشار هذا التريند؟ تعال نفككها بحقائق لا يمكن إنكارها!»
                                                        
                                                        💡 التفنيد: ما يدعيه هذا التريند يخالف المنطق العظيم والدراسات الواقعية، حيث أثبتت التجربة أن التخلي عن المبادئ لا يجلب إلا الضياع.
                                                        
                                                        📖 الدليل: قال تعالى: {فَمَنِ اتَّبَعَ هُدَايَ فَلَا يَضِلُّ وَلَا يَشْقَى}.
                                                        
                                                        ✨ البديل: اعتز بهويتك وكن منارات للحق ولا تتبع المتشابهات!
                                                    """.trimIndent()
                                                } finally {
                                                    isGeneratingRefutation = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !isGeneratingRefutation
                                    ) {
                                        if (isGeneratingRefutation) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepSlate, strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("جاري تفكيك الشبهة وتوليد الرد...", color = DeepSlate, fontFamily = CairoFont)
                                        } else {
                                            Icon(Icons.Default.Shield, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("تفكيك وتوليد الرد القاطع ✨", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (generatedRefutationScript.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
                                                .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                .padding(14.dp)
                                        ) {
                                            Column {
                                                Text(generatedRefutationScript, color = Color.White, fontFamily = NotoSansFont, fontSize = 14.sp, lineHeight = 20.sp)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            onOpenTeleprompterWithText(generatedRefutationScript)
                                                        },
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Mic, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("تسجيل بالمُلقن 🎙️", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            onStartScriptWithText(generatedRefutationScript)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Movie, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("بدء المونتاج 🎬", color = DeepSlate, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                3 -> {
                    // TAB 3: Guardian Badges & Impact Stats
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Brush.radialGradient(listOf(GoldPrimary, Color(0xFF9A7B1C)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(44.dp))
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("وسام المُرابط الرقمي لرد الفتن", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("«الْمُجَاهِدُ مَنْ جَاهَدَ نَفْسَهُ فِي طَاعَةِ اللَّهِ، وَالْمُهَاجِرُ مَنْ هَجَرَ مَا نَهَى اللَّهُ عَنْهُ»", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, textAlign = TextAlign.Center)

                                    Spacer(modifier = Modifier.height(20.dp))

Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                Translator.tr("لا توجد بيانات عرضة للعرض بعد"),
                color = Color.Gray,
                fontFamily = NotoSansFont,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
                                }
                            }
                        }

                        item {
                            Text("أوسمة الإنجاز والمدافعة 🏅", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        item {
                            BadgeCard(
                                title = "وسام قاهر الشبهات 🛡️",
                                desc = "نشر 5 مقاطع رد وتفنيد على التريندات الهابطة.",
                                isUnlocked = true
                            )
                        }

                        item {
                            BadgeCard(
                                title = "وسام حارس الوعي ⚡",
                                desc = "تحقيق أكثر من 10,000 مشاهدة لمقاطع المدافعة والوعي.",
                                isUnlocked = true
                            )
                        }

                        item {
                            BadgeCard(
                                title = "وسام سيف الحكمة 🗡️",
                                desc = "استخدام أداة تفنيد الذكاء الاصطناعي أكثر من 15 مرة.",
                                isUnlocked = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrendAlertCard(
    alert: ActiveTrendAlert,
    onGenerateRebutt: () -> Unit,
    onOpenTeleprompter: () -> Unit,
    onCopyDetails: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (alert.threatLevel.contains("حرج")) Color(0xFF7F1D1D) else Color(0xFF151B2B),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("الخطورة: ${alert.threatLevel}", color = if (alert.threatLevel.contains("حرج")) Color(0xFFEF4444) else GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Text(alert.category, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(alert.title, color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(alert.trendSummary, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 18.sp)

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text("💡 محور التفنيد ومدافعة الفتنة:", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(alert.coreArgument, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cardContext = LocalContext.current
                IconButton(
                    onClick = {
                        AppNotificationService.sendNotification(
                            cardContext,
                            "🚨 تنبيه استنفار عاجل: ${alert.title}",
                            "تم إرسال إشعار لمجتمع صناع المحتوى لبدء المدافعة بنشر الفيديوهات المجهرة.",
                            isTrendAlert = true
                        )
                        Toast.makeText(cardContext, "تم بث تنبيه الاستنفار بنجاح لكافة صناع المحتوى! 🔔", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF151B2B), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = "بث تنبيه عاجل", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                }

                OutlinedButton(
                    onClick = onOpenTeleprompter,
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("المُلقن 🎙️", color = GoldPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                }

                Button(
                    onClick = onGenerateRebutt,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إنتاج رد ومدافعة 🎬", color = DeepSlate, fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatBox(title: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFF0B0F19), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(value, color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(title, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
    }
}

@Composable
fun BadgeCard(title: String, desc: String, isUnlocked: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isUnlocked) CardSurface else Color(0xFF0B0F19)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isUnlocked) GoldPrimary.copy(alpha = 0.5f) else Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isUnlocked) GoldPrimary.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isUnlocked) GoldPrimary else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = if (isUnlocked) Color.White else Color.Gray, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(desc, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            if (isUnlocked) {
                Text("مكتمل ✅", color = Color(0xFF4CAF50), fontFamily = NotoSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
