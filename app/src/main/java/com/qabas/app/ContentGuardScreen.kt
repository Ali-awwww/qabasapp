package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleKeyRequiredBanner
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

data class ActiveTrendAlert(
    val id: String,
    val title: String,
    val threatLevel: String,
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
    val theme = QabasTheme.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val hasAiEngine = remember { ServiceAvailabilityRegistry.hasAnyAiAssistantEngine(context) }

    val guardPrefs = remember { context.getSharedPreferences("qabas_content_guard_prefs", Context.MODE_PRIVATE) }
    var inspectionsCount by remember { mutableIntStateOf(guardPrefs.getInt("inspections_count", 0)) }
    var approvedCount by remember { mutableIntStateOf(guardPrefs.getInt("approved_count", 0)) }
    var refutationsCount by remember { mutableIntStateOf(guardPrefs.getInt("refutations_count", 0)) }
    val alertsSharedCount by remember { mutableIntStateOf(guardPrefs.getInt("alerts_shared_count", 0)) }

    val impactPercent = ((inspectionsCount * 10) + (approvedCount * 15) + (refutationsCount * 20) + (alertsSharedCount * 5)).coerceIn(0, 100)

    var selectedTab by remember { mutableIntStateOf(0) }

    var inspectInputText by remember {
        mutableStateOf("عن أبي هريرة رضي الله عنه قال: قال رسول الله ﷺ: «من كان يؤمن بالله واليوم الآخر فليقل خيراً أو ليصمت». مقطع قصير يوضح أثر الكلمة الطيبة في بناء القلوب وتجنب الفتن في وسائل التواصل.")
    }
    var inspectionResult by remember { mutableStateOf<ContentInspectionResult?>(null) }
    var isInspecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        inspectionResult = ContentFilterService.quickInspect(inspectInputText)
    }

    val activeAlerts = remember {
        listOf(
            ActiveTrendAlert(
                id = "alert_1",
                title = "موجة التشكيك في جدوى الاستقامة والالتزام",
                threatLevel = "حرج",
                category = "تزكية وعقيدة",
                trendSummary = "انتشار فيديوهات تدعي أن الالتزام بالدين يسبب التضييق والاكتئاب، والترويج للانفلات بدعوى الحرية.",
                coreArgument = "السعادة الحقيقية والطمأنينة هي في الاتساق مع فطرة الله {أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ}، وملاحقة الشهوات بلا ضابط تورث الشتات والضياع.",
                suggestedHook = "«يقولون لك الالتزام يسرق شبابك؟ تعال أقول لك حقيقة لا يريدونك أن تفهمها...»",
                evidenceText = "قوله تعالى: {وَمَنْ أَعْرَضَ عَن ذِكْرِي فَإِنَّ لَهُ مَعِيشَةً ضَنكًا}، وحديث «عجباً لأمر المؤمن إن أمره كله خير»."
            ),
            ActiveTrendAlert(
                id = "alert_2",
                title = "تريند السخرية من القيم الأسرية وبر الوالدين",
                threatLevel = "مرتفع",
                category = "أخلاق واجتماع",
                trendSummary = "مقاطع كوميدية تعزز عقوق الوالدين وتصور الأب والأم كعائق أمام طموح الشاب وسعادته.",
                coreArgument = "الوالدان هم الباب الأوسط للجنة، والعقوق سبب محق البركة والتوفيق في الدنيا قبل الآخرة. النجاح الحقيقي يبدأ من رضاهما.",
                suggestedHook = "«فيديو واحد قد يخسرك أعظم باب للجنة وأنت تضحك! انتبه مما تشاهده...»",
                evidenceText = "قوله تعالى: {وَبِالْوَالِدَيْنِ إِحْسَانًا}، وحديث «رغم أنف ثم رغم أنف من أدرك أبويه عند الكبر أحدهما أو كلاهما فلم يدخل الجنة»."
            ),
            ActiveTrendAlert(
                id = "alert_3",
                title = "موجة اليأس والإحباط من واقع الأمة",
                threatLevel = "متوسط",
                category = "وعي وأمل",
                trendSummary = "نشر مقاطع تبث العجز وتوحي بانتصار الباطل الدائم وعدم جدوى أي عمل إصلاحي.",
                coreArgument = "المسلم لا يعرف اليأس، وسنة الله متجددة في تداول الأيام. الواجب على كل مسلم غرس غراسه وبذل وسعه دون انتظار النتائج.",
                suggestedHook = "«إن ظننت أن الباطل قد انتصر نهائياً، فأنت لم تقرأ التاريخ ولم تفهم قوله تعالى...»",
                evidenceText = "قوله تعالى: {وَلا تَهِنُوا وَلا تَحْزَنُوا وَأَنْتُمُ الأَعْلَوْنَ إِنْ كُنْتُمْ مُؤْمِنِينَ}، وحديث «إن قامت الساعة وفي يد أحدكم فسيلة فليغرسها»."
            )
        )
    }

    var inputTrendText by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf("رد عقلاني ودعوي هادئ") }
    val tones = listOf("رد عقلاني ودعوي هادئ", "رد حماسي وقوي", "كشف التناقض والبديل الإيجابي")
    var isGeneratingRefutation by remember { mutableStateOf(false) }
    var generatedRefutationScript by remember { mutableStateOf("") }
    var refutationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            Column(modifier = Modifier.background(theme.background)) {
                AppleTopBar(
                    title = "مرصد الثغور ومدافعة الفتن",
                    subtitle = "فحص المحتوى وتفنيد الشبهات بالحق والحكمة",
                    onBack = onBack
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    AppleSegmentedControl(
                        items = listOf(
                            0 to "الفاحص",
                            1 to "المرصد",
                            2 to "التفنيد",
                            3 to "الأوسمة"
                        ),
                        selectedItem = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AppleCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                cornerRadius = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("النشاط الفعلي:", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (refutationsCount >= 5) "مُرابط متقدم" else "حارس المحتوى",
                            color = theme.primary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Surface(
                        color = theme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "الأثر: $impactPercent%",
                            color = theme.primary,
                            fontFamily = RobotoMonoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            AppleCard(cornerRadius = 18.dp) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(theme.primary.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Security, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("فاحص النصوص والضوابط الشرعية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                                            Text("تحقق فوري من سلامة النص قبل التوليد والنشر.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = inspectInputText,
                                        onValueChange = {
                                            inspectInputText = it
                                            inspectionResult = ContentFilterService.quickInspect(it)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 110.dp),
                                        placeholder = { Text("الصق هنا فكرة الفيديو أو الآية أو السيناريو لفحصه...", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        label = { Text("نص الفكرة أو السيناريو", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.primary,
                                            unfocusedBorderColor = theme.separator.copy(alpha = 0.6f),
                                            focusedTextColor = theme.textPrimary,
                                            unfocusedTextColor = theme.textPrimary,
                                            focusedContainerColor = theme.secondarySurface,
                                            unfocusedContainerColor = theme.secondarySurface
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("أمثلة سريعة للتجربة:", color = theme.textSecondary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                                            label = { Text("مناسب", fontSize = 11.5.sp, fontFamily = CairoFont) },
                                            colors = FilterChipDefaults.filterChipColors(containerColor = theme.success.copy(alpha = 0.14f), labelColor = theme.success)
                                        )

                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                inspectInputText = "أقسم عليك أن تنشر هذا المقطع لعشرة أشخاص وإلا ستحل عليك مصيبة اليوم! معجزة حقيقية حدثت لشخص بعد قراءة هذا المنشور."
                                                inspectionResult = ContentFilterService.quickInspect(inspectInputText)
                                            },
                                            label = { Text("يحتاج مراجعة", fontSize = 11.5.sp, fontFamily = CairoFont) },
                                            colors = FilterChipDefaults.filterChipColors(containerColor = theme.warning.copy(alpha = 0.14f), labelColor = theme.warning)
                                        )

                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                inspectInputText = "فيديو يحتوي على ألفاظ بذيئة وشتم وسخرية من الأنبياء والدين وتحديات راقصة عارية."
                                                inspectionResult = ContentFilterService.quickInspect(inspectInputText)
                                            },
                                            label = { Text("مرفوض", fontSize = 11.5.sp, fontFamily = CairoFont) },
                                            colors = FilterChipDefaults.filterChipColors(containerColor = theme.destructive.copy(alpha = 0.14f), labelColor = theme.destructive)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    ApplePrimaryButton(
                                        text = if (isInspecting) "جاري الفحص المعمق..." else "فحص وتدقيق عميق بالذكاء الاصطناعي",
                                        icon = Icons.Default.Verified,
                                        isLoading = isInspecting,
                                        enabled = !isInspecting,
                                        onClick = {
                                            if (inspectInputText.isBlank()) {
                                                Toast.makeText(context, "يرجى كتابة نص للفحص أولاً", Toast.LENGTH_SHORT).show()
                                                return@ApplePrimaryButton
                                            }
                                            isInspecting = true
                                            scope.launch {
                                                val res = ContentFilterService.inspectWithAI(inspectInputText)
                                                inspectionResult = res
                                                isInspecting = false

                                                inspectionsCount += 1
                                                val editor = guardPrefs.edit().putInt("inspections_count", inspectionsCount)
                                                if (res.verdict == GuardVerdict.APPROPRIATE) {
                                                    approvedCount += 1
                                                    editor.putInt("approved_count", approvedCount)
                                                }
                                                editor.apply()

                                                CloudServices.Database.recordContentGuardCheck(
                                                    text = inspectInputText,
                                                    verdict = res.verdict.name,
                                                    score = res.score,
                                                    reason = res.reason
                                                )
                                                AppServices.getAnalyticsService(context).logEvent(
                                                    "content_guard_inspection",
                                                    mapOf("verdict" to res.verdict.name, "score" to res.score, "source" to "guard_hub")
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        inspectionResult?.let { res ->
                            item {
                                val verdictColor = when (res.verdict) {
                                    GuardVerdict.APPROPRIATE -> theme.success
                                    GuardVerdict.NEEDS_REVIEW -> theme.warning
                                    GuardVerdict.REJECTED -> theme.destructive
                                }

                                AppleCard(cornerRadius = 18.dp) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(verdictColor.copy(alpha = 0.16f)),
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
                                                    Text(res.verdict.badge, color = verdictColor, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                    Text(res.title, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                }
                                            }

                                            Surface(
                                                color = verdictColor.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(10.dp)
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

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Surface(
                                            color = theme.secondarySurface,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("السبب والنتيجة:", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(res.reason, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 13.sp, lineHeight = 19.sp)
                                            }
                                        }

                                        if (res.warnings.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("ملاحظات تدقيقية:", color = theme.destructive, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            res.warnings.forEach { warning ->
                                                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = theme.destructive, modifier = Modifier.size(8.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(warning, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        if (res.suggestions.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("توصيات التحسين:", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            res.suggestions.forEach { sug ->
                                                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = theme.primary, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(sug, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        res.improvedScript?.let { improved ->
                                            if (improved.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Text("الصياغة المقترحة:", color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Surface(
                                                    color = theme.secondarySurface,
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(improved, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(12.dp))
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        AppleSecondaryButton(
                                                            text = "نسخ",
                                                            icon = Icons.Default.ContentCopy,
                                                            onClick = {
                                                                clipboardManager.setText(AnnotatedString(improved))
                                                                Toast.makeText(context, "تم نسخ النص المصوب", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                    Box(modifier = Modifier.weight(1.2f)) {
                                                        ApplePrimaryButton(
                                                            text = "بدء المونتاج به",
                                                            icon = Icons.Default.Movie,
                                                            onClick = { onStartScriptWithText(improved) }
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

                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Surface(
                                color = theme.destructive.copy(alpha = if (theme.isDark) 0.16f else 0.08f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(0.5.dp, theme.destructive.copy(alpha = 0.4f))
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = theme.destructive, modifier = Modifier.size(30.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("التنبيه الفوري للثغور الرقمية", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                                        Text("قضايا تحتاج لمدافعة علمية ورسائل وعي متزنة.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 17.sp)
                                    }
                                }
                            }
                        }

                        items(activeAlerts, key = { it.id }) { alert ->
                            TrendAlertCard(
                                alert = alert,
                                onGenerateRebutt = {
                                    val fullScript = """
                                        سيناريو مدافعة ورد على: ${alert.title}
                                        
                                        الخطاف الجاذب (0-3 ثوان):
                                        ${alert.suggestedHook}
                                        
                                        التفنيد والرد العلمي:
                                        ${alert.coreArgument}
                                        
                                        الدليل الشرعي والأثر:
                                        ${alert.evidenceText}
                                        
                                        الرسالة الختامية:
                                        «لا تدع الشبهات تعصف بوعيك، تمسك بنور الحق واعتز بهويتك الإسلامية.»
                                    """.trimIndent()
                                    onStartScriptWithText(fullScript)
                                },
                                onOpenTeleprompter = {
                                    onOpenTeleprompterWithText("${alert.suggestedHook}\n\n${alert.coreArgument}\n\n${alert.evidenceText}")
                                },
                                onCopyDetails = {
                                    val text = "${alert.title}\nالرد: ${alert.coreArgument}\nالدليل: ${alert.evidenceText}"
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "تم نسخ تفاصيل الرد", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        if (!hasAiEngine) {
                            item {
                                AppleKeyRequiredBanner(
                                    serviceName = "محرك تفكيك الشبهات",
                                    reason = "لم يتم ضبط مفتاح API أو نموذج محلي؛ عند تعذر الاتصال ستظهر رسالة خطأ واضحة دون استخدام نصوص وهمية."
                                )
                            }
                        }

                        item {
                            AppleCard(cornerRadius = 18.dp) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = theme.primary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("مُفكك التريندات ومولّد الردود", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("أدخل اسم التريند أو الشبهة المنتشرة وسيعمل الذكاء الاصطناعي على تفكيكها وصياغة سيناريو رد علمي متزن.", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 18.sp)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = inputTrendText,
                                        onValueChange = { inputTrendText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 100.dp),
                                        placeholder = { Text("مثال: تريند السخرية من الحجاب، أو شبهة فكرية منتشرة...", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                        label = { Text("وصف التريند أو الشبهة", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = theme.primary,
                                            unfocusedBorderColor = theme.separator.copy(alpha = 0.6f),
                                            focusedTextColor = theme.textPrimary,
                                            unfocusedTextColor = theme.textPrimary,
                                            focusedContainerColor = theme.secondarySurface,
                                            unfocusedContainerColor = theme.secondarySurface
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("أسلوب الرد والتفنيد:", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(tones) { tone ->
                                            val isSelected = selectedTone == tone
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedTone = tone },
                                                label = { Text(tone, fontFamily = NotoSansFont, fontSize = 12.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = theme.primary,
                                                    selectedLabelColor = Color.White,
                                                    containerColor = theme.secondarySurface,
                                                    labelColor = theme.textPrimary
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    ApplePrimaryButton(
                                        text = if (isGeneratingRefutation) "جاري تفكيك الشبهة..." else "تفكيك وتوليد الرد القاطع",
                                        icon = Icons.Default.Shield,
                                        isLoading = isGeneratingRefutation,
                                        enabled = !isGeneratingRefutation,
                                        onClick = {
                                            if (inputTrendText.isBlank()) {
                                                Toast.makeText(context, "يرجى كتابة الشبهة أو التريند أولاً", Toast.LENGTH_SHORT).show()
                                                return@ApplePrimaryButton
                                            }
                                            isGeneratingRefutation = true
                                            refutationError = null
                                            scope.launch {
                                                try {
                                                    val prompt = """
                                                        أنت خبير في التوعية الإسلامية ومدافعة الشبهات الرقمية.
                                                        قم بصياغة سيناريو فيديو قصير (60 ثانية) لتفنيد والرد على الموضوع التالي: "$inputTrendText".
                                                        أسلوب الرد المطلوب: "$selectedTone".
                                                        الهيكلية:
                                                        1) خطاف جاذب في أول 3 ثوان بدون تنفير.
                                                        2) تفكيك الفكرة الخاطئة وإظهار تناقضها بأسلوب عقلاني.
                                                        3) الدليل الشرعي أو الفطري أو العلمي المؤيد للحق.
                                                        4) البديل الإيجابي ودعوة واضحة للاعتزاز بالدين.
                                                    """.trimIndent()
                                                    val result = AppServices.chatWithAssistant(listOf(Pair(true, prompt)))
                                                    if (result.isBlank()) {
                                                        refutationError = "لم تتوفر استجابة من محرك الذكاء الاصطناعي. تحقق من الاتصال أو مفاتيح API."
                                                    } else {
                                                        generatedRefutationScript = result
                                                        refutationsCount += 1
                                                        guardPrefs.edit().putInt("refutations_count", refutationsCount).apply()
                                                    }
                                                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                                    refutationError = "تعذر توليد الرد: ${e.message ?: "تحقق من اتصال الإنترنت أو مفاتيح AI"}"
                                                } finally {
                                                    isGeneratingRefutation = false
                                                }
                                            }
                                        }
                                    )

                                    if (!refutationError.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(refutationError!!, color = theme.destructive, fontFamily = NotoSansFont, fontSize = 12.sp)
                                    }

                                    if (generatedRefutationScript.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Surface(
                                            color = theme.secondarySurface,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(generatedRefutationScript, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 13.5.sp, lineHeight = 20.sp)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        AppleSecondaryButton(
                                                            text = "تسجيل بالمُلقن",
                                                            icon = Icons.Default.Mic,
                                                            onClick = { onOpenTeleprompterWithText(generatedRefutationScript) }
                                                        )
                                                    }
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        ApplePrimaryButton(
                                                            text = "بدء المونتاج",
                                                            icon = Icons.Default.Movie,
                                                            onClick = { onStartScriptWithText(generatedRefutationScript) }
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

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        item {
                            AppleCard(cornerRadius = 18.dp) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(theme.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = theme.primary, modifier = Modifier.size(36.dp))
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("إحصاءات وأوسمة المدافعة", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text("«الْمُجَاهِدُ مَنْ جَاهَدَ نَفْسَهُ فِي طَاعَةِ اللَّهِ، وَالْمُهَاجِرُ مَنْ هَجَرَ مَا نَهَى اللَّهُ عَنْهُ»", color = theme.primary, fontFamily = NotoSansFont, fontSize = 12.sp, textAlign = TextAlign.Center)

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            StatBox(title = "عمليات الفحص", value = inspectionsCount.toString())
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            StatBox(title = "نصوص مجازة", value = approvedCount.toString())
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            StatBox(title = "ردود مولدة", value = refutationsCount.toString())
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("أوسمة الإنجاز والمدافعة", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        item {
                            BadgeCard(
                                title = "وسام الفحص الأول",
                                desc = "إجراء فحص نص واحد على الأقل للتثبت من سلامة المحتوى ($inspectionsCount / 1).",
                                isUnlocked = inspectionsCount >= 1
                            )
                        }

                        item {
                            BadgeCard(
                                title = "وسام المحتوى الموثوق",
                                desc = "اعتماد 3 نصوص مجازة بعد التدقيق المعمق ($approvedCount / 3).",
                                isUnlocked = approvedCount >= 3
                            )
                        }

                        item {
                            BadgeCard(
                                title = "وسام سيف الحكمة",
                                desc = "توليد 5 سيناريوهات مدافعة وتفنيد عبر الذكاء الاصطناعي ($refutationsCount / 5).",
                                isUnlocked = refutationsCount >= 5
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
    val context = LocalContext.current
    val theme = QabasTheme.current
    val isCritical = alert.threatLevel.contains("حرج")
    val levelColor = if (isCritical) theme.destructive else theme.warning

    AppleCard(cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = levelColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "الخطورة: ${alert.threatLevel}",
                        color = levelColor,
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(alert.category, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(alert.title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(alert.trendSummary, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.5.sp, lineHeight = 19.sp)

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("محور التفنيد ومدافعة الفتنة:", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(alert.coreArgument, color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 12.5.sp, lineHeight = 19.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        AppNotificationService.sendNotification(
                            context,
                            "تنبيه استنفار عاجل: ${alert.title}",
                            "تذكير بنشر محتوى توعوي هادف حول هذا الموضوع.",
                            isTrendAlert = true
                        )
                        val prefs = context.getSharedPreferences("qabas_content_guard_prefs", Context.MODE_PRIVATE)
                        val next = prefs.getInt("alerts_shared_count", 0) + 1
                        prefs.edit().putInt("alerts_shared_count", next).apply()
                        Toast.makeText(context, "تم إرسال التنبيه عبر نظام الإشعارات", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.destructive.copy(alpha = 0.14f))
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = "بث تنبيه", tint = theme.destructive, modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = onCopyDetails,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.secondarySurface)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = theme.primary, modifier = Modifier.size(19.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    AppleSecondaryButton(text = "المُلقن", onClick = onOpenTeleprompter)
                }

                Box(modifier = Modifier.weight(1.2f)) {
                    ApplePrimaryButton(text = "إنتاج رد", icon = Icons.Default.Movie, onClick = onGenerateRebutt)
                }
            }
        }
    }
}

@Composable
fun StatBox(title: String, value: String) {
    val theme = QabasTheme.current
    Surface(
        color = theme.secondarySurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Text(value, color = theme.primary, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.5.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun BadgeCard(title: String, desc: String, isUnlocked: Boolean) {
    val theme = QabasTheme.current
    AppleCard(cornerRadius = 14.dp) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isUnlocked) theme.primary.copy(alpha = 0.16f) else theme.secondarySurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isUnlocked) theme.primary else theme.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = if (isUnlocked) theme.textPrimary else theme.textSecondary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(desc, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
            }
            if (isUnlocked) {
                Surface(
                    color = theme.success.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "مكتمل",
                        color = theme.success,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
