package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*

/**
 * استوديو قبس الإبداعي، منظم في فئات متخصصة للفيديو والصور والصوت والمكتبة والنشر.
 * تُدار مفاتيح الخدمات الخارجية من نافذة الإعدادات المخصصة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioGatewayScreen(
    onBack: () -> Unit,
    onNavigateToHiggsfield: () -> Unit,
    onNavigateToMediaBrowser: () -> Unit,
    onNavigateToAiStudio: () -> Unit,
    onNavigateToProEditor: () -> Unit,
    onNavigateToMoneyPrinter: () -> Unit,
    onNavigateToPhotoStudio: () -> Unit,
    onNavigateToVoiceStudio: () -> Unit,
    onNavigateToYouTubeStudio: () -> Unit,
    onNavigateToTeleprompter: () -> Unit,
    onNavigateToStyleCloner: () -> Unit,
    onNavigateToHadithStudio: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToAiStudioAgent: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val primaryColor = qabasPrimary()

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var selectedCategoryKey by remember { mutableStateOf("video") }
    val categories = remember {
        listOf(
            StudioDepartment(
                key = "video",
                title = "🎬 الفيديو",
                description = "التوليد والتحرير والتسجيل",
                toolCount = 7,
                icon = Icons.Default.MovieFilter,
                accentColor = Color(0xFF00D4E8)
            ),
            StudioDepartment(
                key = "images",
                title = "🎨 الصور والتصميم",
                description = "تصميم الصور والبطاقات",
                toolCount = 2,
                icon = Icons.Default.Image,
                accentColor = Color(0xFFF5B942)
            ),
            StudioDepartment(
                key = "audio",
                title = "🎙️ الصوت",
                description = "التعليق الصوتي والأدوات الصوتية",
                toolCount = 1,
                icon = Icons.Default.GraphicEq,
                accentColor = Color(0xFFE879B9)
            ),
            StudioDepartment(
                key = "library",
                title = "🗂️ مكتبة الوسائط",
                description = "صورك ومقاطعك وملفاتك الصوتية المحفوظة",
                toolCount = 1,
                icon = Icons.Default.PermMedia,
                accentColor = Color(0xFF34D399)
            ),
            StudioDepartment(
                key = "publishing",
                title = "📣 النشر والتقارير",
                description = "تجهيز المنصات ومراجعة الأداء",
                toolCount = 2,
                icon = Icons.Default.QueryStats,
                accentColor = Color(0xFF60A5FA)
            )
        )
    }

    Scaffold(
        containerColor = Color(0xFF030712),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CutCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.25f), primaryColor.copy(alpha = 0.05f))))
                                .border(1.dp, primaryColor.copy(alpha = 0.5f), CutCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "استوديو قبس الإبداعي",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                "CREATIVE STUDIO // SERVICE HUB",
                                color = primaryColor.copy(alpha = 0.6f),
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(Icons.Default.VpnKey, contentDescription = "مفاتيح الـ API", tint = primaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF030712).copy(alpha = 0.95f))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF080D1A), Color(0xFF030712))
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            StudioOverviewCard(primaryColor = primaryColor)

            Spacer(modifier = Modifier.height(18.dp))

            SectionHeader("الأقسام المتخصصة", "WORKSPACES", "✦")
            Text(
                text = "خمسة مسارات تنظّم أدوات الإنتاج والوسائط والنشر.",
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.chunked(2).forEach { departmentRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        departmentRow.forEach { department ->
                            StudioDepartmentCard(
                                department = department,
                                selected = selectedCategoryKey == department.key,
                                modifier = Modifier.weight(if (departmentRow.size == 1) 2f else 1f),
                                onClick = { selectedCategoryKey = department.key }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val currentFilter = selectedCategoryKey

            // Render only the tools that belong to the selected route.
            when (currentFilter) {
                "video" -> {
                    SectionHeader("إنتاج الفيديو", "VIDEO", "🎬")
                    StudioFeatureCard(
                        title = "Higgsfield — فيديو من نص أو صورة",
                        description = "مساحة مستقلة لـKling 4.0. تتطلب ربط مفتاح API، ويُطلب تأكيد قبل التوليد.",
                        badge = "HIGGSFIELD",
                        badgeColor = Color(0xFF00F2FE),
                        icon = Icons.Default.MovieFilter,
                        onClick = onNavigateToHiggsfield
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "مشروع فيديو متكامل",
                        description = "ابدأ من الفكرة والملخص ثم تابع مسار الإنتاج المعتاد؛ اختيار Higgsfield يتم لاحقًا إن كان متاحًا.",
                        badge = "PROJECT FLOW",
                        badgeColor = primaryColor,
                        icon = Icons.Default.EditNote,
                        onClick = onNavigateToAiStudio
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "إنتاج ريلز آلي — MoneyPrinter",
                        description = "مسار آلي لإعداد الريلز؛ تختلف خيارات المتابعة حسب نجاح المحرك المحلي أو الخادم.",
                        badge = "AUTOMATION",
                        badgeColor = Color(0xFF10B981),
                        icon = Icons.Default.Bolt,
                        onClick = onNavigateToMoneyPrinter
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "المحرر الاحترافي (Pro Editor) ✂️",
                        description = "مونتاج وتحرير المشاهد والمسارات على الخط الزمني.",
                        badge = "PRO TOOLS",
                        badgeColor = Color(0xFF38BDF8),
                        icon = Icons.Default.ContentCut,
                        onClick = onNavigateToProEditor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "التصوير والملقّن",
                        description = "قراءة النص أثناء التسجيل ثم الانتقال إلى تحرير الفيديو.",
                        badge = "RECORD",
                        badgeColor = Color(0xFF10B981),
                        icon = Icons.Default.Videocam,
                        onClick = onNavigateToTeleprompter
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "تحليل أسلوب الفيديو",
                        description = "حلّل مرجعًا بصريًا لبناء وصف أسلوب لمشروعك.",
                        badge = "STYLE",
                        badgeColor = Color(0xFFF59E0B),
                        icon = Icons.Default.AutoAwesome,
                        onClick = onNavigateToStyleCloner
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "مساعد إنتاج المحتوى",
                        description = "افتح مساعد الذكاء الاصطناعي لمتابعة مهام الإنتاج.",
                        badge = "AI ASSISTANT",
                        badgeColor = Color(0xFF38BDF8),
                        icon = Icons.Default.SmartToy,
                        onClick = onNavigateToAiStudioAgent
                    )
                }
                "images" -> {
                    SectionHeader("الصور والتصميم", "IMAGES", "🎨")
                    StudioFeatureCard(
                        title = "استوديو الصور",
                        description = "تصميم وتحرير الصور؛ يمكن اختيار Leonardo لخلفيات AI عند توفر المفتاح.",
                        badge = "PHOTO STUDIO",
                        badgeColor = Color(0xFFF59E0B),
                        icon = Icons.Default.Image,
                        onClick = onNavigateToPhotoStudio
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "بطاقات القرآن والحديث",
                        description = "افتح استوديو بطاقات الآيات والأحاديث لصناعة تصميم قابل للحفظ.",
                        badge = "QABAS CARDS",
                        badgeColor = primaryColor,
                        icon = Icons.Default.MenuBook,
                        onClick = onNavigateToHadithStudio
                    )
                }
                "audio" -> {
                    SectionHeader("الصوت", "AUDIO", "🎙️")
                    StudioFeatureCard(
                        title = "VoiceStudio — توليد صوت محلي",
                        description = "يستخدم أصوات Edge مع معالجة سرعة وطبقة؛ الاستنساخ والخادم غير موصولين بمسار التوليد الحالي.",
                        badge = "LOCAL TTS",
                        badgeColor = Color(0xFFEC4899),
                        icon = Icons.Default.GraphicEq,
                        onClick = onNavigateToVoiceStudio
                    )
                }
                "library" -> {
                    SectionHeader("مكتبة الوسائط", "LIBRARY", "🗂️")
                    StudioFeatureCard(
                        title = "الصور والفيديو والصوت المحفوظ",
                        description = "استعرض الوسائط واختر ملفًا لمسارات التحرير المتاحة.",
                        badge = "MEDIA LIBRARY",
                        badgeColor = Color(0xFF10B981),
                        icon = Icons.Default.PermMedia,
                        onClick = onNavigateToMediaBrowser
                    )
                }
                "publishing" -> {
                    SectionHeader("التجهيز والنشر والتقارير", "PUBLISHING", "📣")
                    StudioFeatureCard(
                        title = "تجهيز محتوى يوتيوب",
                        description = "أدوات تجهيز الفيديو والبيانات الوصفية؛ لا تدّعي هذه الشاشة ربط قناة أو نشرًا مباشرًا.",
                        badge = "YOUTUBE TOOLS",
                        badgeColor = Color(0xFFEF4444),
                        icon = Icons.Default.SmartDisplay,
                        onClick = onNavigateToYouTubeStudio
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "إحصاءات التواصل الاجتماعي",
                        description = "افتح شاشة التقارير الاجتماعية المتاحة في التطبيق.",
                        badge = "REPORTS",
                        badgeColor = Color(0xFF38BDF8),
                        icon = Icons.Default.QueryStats,
                        onClick = onNavigateToStats
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Wisdom Note
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                borderColor = primaryColor.copy(alpha = 0.25f),
                shape = CutCornerShape(12.dp)
            ) {
                Text(
                    text = "«تم تنظيم الاستوديو ليكون رفيقك في صناعة الأثر الدعوي المتقن»",
                    color = TextPrimary.copy(alpha = 0.85f),
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showApiKeyDialog) {
            ApiKeyManagerDialog(
                onDismiss = { showApiKeyDialog = false }
            )
        }
    }
}

@Composable
fun ApiKeyManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val primaryColor = qabasPrimary()
    val textMutedColor = Color(0xFF94A3B8)
    val cyanInfoColor = Color(0xFF06B6D4)

    var leonardoKey by remember { mutableStateOf(ApiKeyManager.getLeonardoKey(context)) }
    var showLeonardoKey by remember { mutableStateOf(false) }
    var isTestingLeonardo by remember { mutableStateOf(false) }
    var leonardoTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var higgsfieldKey by remember { mutableStateOf("") }
    var showHiggsfieldKey by remember { mutableStateOf(false) }
    var isTestingHiggsfield by remember { mutableStateOf(false) }
    var isSavingHiggsfield by remember { mutableStateOf(false) }
    var isHiggsfieldConfigured by remember { mutableStateOf(false) }
    var higgsfieldTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(Unit) {
        isHiggsfieldConfigured = HiggsfieldVideoService.isConfigured(context)
    }

    val openUrl: (String) -> Unit = { url ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "لا يوجد متصفح متاح لفتح الرابط", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح المتصفح: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val sanitizeKey: (String) -> String = { raw ->
        raw.trim().removeSurrounding("\"").removeSurrounding("'").removePrefix("Bearer ").trim()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 16.dp),
            backgroundColor = Color(0xFF0B132B).copy(alpha = 0.98f),
            borderColor = primaryColor.copy(alpha = 0.45f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.15f))
                                .border(1.dp, primaryColor.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "إدارة مفاتيح الـ API",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "ربط مباشر مع منصات الذكاء الاصطناعي",
                                color = textMutedColor,
                                fontFamily = CairoFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                // Educational / Honesty Explainer Card
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF1E293B).copy(alpha = 0.7f),
                    borderColor = cyanInfoColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = cyanInfoColor,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "لماذا يتطلب المفتاح خطوات ربط مباشرة؟",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                "لحماية خصوصيتك ورصيدك، تمنع منصات الذكاء الاصطناعي العالمية أي تطبيق خارجي من استخراج المفاتيح سراً بدون تسجيلك وتفويضك. افتح لوحة المزود من زر «احصل على المفتاح»، ثم الصق المفتاح واحفظه. تُدار مفاتيح Higgsfield داخل خزنة الخادم المشفرة ولا تبقى على الجهاز.",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // ── 1. Leonardo.ai Section ──
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF131D38).copy(alpha = 0.85f),
                    borderColor = if (leonardoKey.isNotBlank()) primaryColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🖼️", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Leonardo.ai (Vision XL)",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "توليد الصور التشكيلية والبوسترات الدعوية",
                                        color = textMutedColor,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Direct Deep Link Button
                            OutlinedButton(
                                onClick = { openUrl("https://app.leonardo.ai/api-access") },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("احصل على المفتاح ↗", fontFamily = CairoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Action Buttons Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Smart Paste Button
                            Button(
                                onClick = {
                                    val text = clipboardManager.getText()?.text ?: ""
                                    val cleaned = sanitizeKey(text)
                                    if (cleaned.isNotBlank()) {
                                        leonardoKey = cleaned
                                        Toast.makeText(context, "تم استخراج ولصق المفتاح من الحافظة 📋", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "الحافظة فارغة! انسخ المفتاح من الموقع أولاً", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("📋 لصق تلقائي", color = Color.White, fontFamily = CairoFont, fontSize = 10.sp)
                            }

                            // Live Connection Test Button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isTestingLeonardo = true
                                        try {
                                            leonardoTestResult = LeonardoImageService.testConnection(context, leonardoKey)
                                        } catch (_: Exception) {
                                            leonardoTestResult = false to "تعذر فحص الاتصال، حاول مرة أخرى"
                                        } finally {
                                            isTestingLeonardo = false
                                        }
                                    }
                                },
                                enabled = !isTestingLeonardo && leonardoKey.isNotBlank(),
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                if (isTestingLeonardo) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = EmeraldGreen, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("⚡ فحص الاتصال", color = Color.White, fontFamily = CairoFont, fontSize = 10.sp)
                            }

                            // Clear button
                            if (leonardoKey.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        leonardoKey = ""
                                        leonardoTestResult = null
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "مسح", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Text Field
                        OutlinedTextField(
                            value = leonardoKey,
                            onValueChange = {
                                leonardoKey = sanitizeKey(it)
                                leonardoTestResult = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("الصق مفتاح Leonardo API هنا...", color = textMutedColor, fontSize = 11.sp, fontFamily = CairoFont) },
                            visualTransformation = if (showLeonardoKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showLeonardoKey = !showLeonardoKey }) {
                                    Icon(
                                        if (showLeonardoKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            )
                        )

                        // Live Test Feedback
                        leonardoTestResult?.let { result ->
                            val isOk = result.first
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isOk) EmeraldGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isOk) EmeraldGreen else Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    result.second,
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }

                // ── 2. Higgsfield AI Section ──
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF131D38).copy(alpha = 0.85f),
                    borderColor = if (higgsfieldKey.isNotBlank() || isHiggsfieldConfigured) primaryColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎬", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Higgsfield AI Studio",
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "توليد الفيديوهات السينمائية وتحريك المشاهد",
                                        color = textMutedColor,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Direct Deep Link Button
                            OutlinedButton(
                                onClick = { openUrl("https://open.higgsfield.ai/api-keys") },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("احصل على المفتاح ↗", fontFamily = CairoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Action Buttons Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Smart Paste Button
                            Button(
                                onClick = {
                                    val text = clipboardManager.getText()?.text ?: ""
                                    val cleaned = sanitizeKey(text)
                                    if (cleaned.isNotBlank()) {
                                        higgsfieldKey = cleaned
                                        Toast.makeText(context, "تم استخراج ولصق المفتاح من الحافظة 📋", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "الحافظة فارغة! انسخ المفتاح من الموقع أولاً", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("📋 لصق تلقائي", color = Color.White, fontFamily = CairoFont, fontSize = 10.sp)
                            }

                            // Live Connection Test Button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isTestingHiggsfield = true
                                        try {
                                            higgsfieldTestResult = HiggsfieldVideoService.testConnection(context)
                                        } catch (_: Exception) {
                                            higgsfieldTestResult = false to "تعذر فحص تسجيل البوابة، حاول مرة أخرى"
                                        } finally {
                                            isTestingHiggsfield = false
                                        }
                                    }
                                },
                                enabled = !isTestingHiggsfield && !isSavingHiggsfield,
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                if (isTestingHiggsfield) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = EmeraldGreen, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("⚡ حالة الربط", color = Color.White, fontFamily = CairoFont, fontSize = 10.sp)
                            }

                            // Clear button
                            if (higgsfieldKey.isNotEmpty() || isHiggsfieldConfigured) {
                                IconButton(
                                    onClick = {
                                        if (higgsfieldKey.isNotEmpty()) {
                                            higgsfieldKey = ""
                                            higgsfieldTestResult = null
                                        } else {
                                            coroutineScope.launch {
                                                isSavingHiggsfield = true
                                                val removed = PlatformGatewayClient.removeProviderKeySuspend("higgsfield")
                                                isHiggsfieldConfigured = !removed && isHiggsfieldConfigured
                                                higgsfieldTestResult = (removed to if (removed) "تم حذف المفتاح من الخادم." else "تعذر حذف المفتاح من الخادم.")
                                                isSavingHiggsfield = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "مسح", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Text Field
                        OutlinedTextField(
                            value = higgsfieldKey,
                            onValueChange = {
                                higgsfieldKey = sanitizeKey(it)
                                higgsfieldTestResult = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("الصق مفتاح API الكامل من open.higgsfield.ai...", color = textMutedColor, fontSize = 11.sp, fontFamily = CairoFont) },
                            visualTransformation = if (showHiggsfieldKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showHiggsfieldKey = !showHiggsfieldKey }) {
                                    Icon(
                                        if (showHiggsfieldKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            )
                        )

                        Text(
                            if (isHiggsfieldConfigured) "المفتاح محفوظ مركزياً على الخادم؛ لا يُعرض أو يُخزّن على هذا الجهاز." else "يُحفظ المفتاح في الخادم المشفر، ولا تُختبر صلاحيته إلا عند أول طلب توليد.",
                            color = textMutedColor,
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )

                        // Gateway status feedback (registration only, not a paid provider validation)
                        higgsfieldTestResult?.let { result ->
                            val isOk = result.first
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isOk) EmeraldGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isOk) EmeraldGreen else Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    result.second,
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }

                // ── Save & Dismiss Actions ──
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSavingHiggsfield = true
                            val keyToSave = sanitizeKey(higgsfieldKey)
                            val higgsfieldSaved = keyToSave.isBlank() || PlatformGatewayClient.saveProviderKeySuspend("higgsfield", keyToSave)
                            if (higgsfieldSaved) {
                                ApiKeyManager.saveLeonardoKey(context, leonardoKey)
                                if (keyToSave.isNotBlank()) isHiggsfieldConfigured = true
                                higgsfieldKey = ""
                                Toast.makeText(context, "تم حفظ إعدادات Leonardo ومفتاح Higgsfield على الخادم الآمن ✓", Toast.LENGTH_LONG).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "تعذر حفظ مفتاح Higgsfield. تحقق من صلاحية حساب المطور واتصال بوابة قبس.", Toast.LENGTH_LONG).show()
                            }
                            isSavingHiggsfield = false
                        }
                    },
                    enabled = !isSavingHiggsfield,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSavingHiggsfield) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DeepSlate, strokeWidth = 2.dp)
                    else Text("حفظ آمن وإغلاق ✓", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                }
            }
        }
    }
}

private data class StudioDepartment(
    val key: String,
    val title: String,
    val description: String,
    val toolCount: Int,
    val icon: ImageVector,
    val accentColor: Color
) {
    val toolCountLabel: String
        get() = when (toolCount) {
            1 -> "أداة واحدة"
            2 -> "أداتان"
            else -> "$toolCount أدوات"
        }
}

@Composable
private fun StudioDepartmentCard(
    department: StudioDepartment,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primaryColor = qabasPrimary()
    val accent = if (selected) primaryColor else department.accentColor

    GlassmorphicSurface(
        modifier = modifier
            .testTag("studio_department_${department.key}")
            .clickable(onClick = onClick),
        backgroundColor = if (selected) Color(0xFF102335) else Color(0xFF0B132B).copy(alpha = 0.52f),
        borderColor = if (selected) primaryColor.copy(alpha = 0.72f) else department.accentColor.copy(alpha = 0.24f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = department.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = department.toolCountLabel,
                    color = if (selected) primaryColor else Color(0xFF94A3B8),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp
                )
            }

            Text(
                text = department.title,
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = department.description,
                color = Color(0xFFB0BDCC),
                fontFamily = CairoFont,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, codeTag: String, emoji: String = "✦") {
    val primaryColor = qabasPrimary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 12.dp)
            .testTag("studio_section_${codeTag.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
                Text(
                    text = codeTag,
                    color = primaryColor.copy(alpha = 0.5f),
                    fontFamily = RobotoMonoFont,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

@Composable
private fun StudioOverviewCard(primaryColor: Color) {
    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFF030712).copy(alpha = 0.85f),
        borderColor = primaryColor.copy(alpha = 0.35f),
        shape = CutCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "مساحات الإبداع في قبس ✦",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "اختر مجال العمل لعرض الأدوات ومساراتها بوضوح.",
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

        }
    }
}

@Composable
private fun StudioFeatureCard(
    title: String,
    description: String,
    badge: String,
    badgeColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    GlassmorphicSurface(
        modifier = Modifier
            .testTag("studio_feature_${badge.lowercase().replace(' ', '_')}")
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = Color(0xFF0B132B).copy(alpha = 0.45f),
        borderColor = badgeColor.copy(alpha = 0.25f),
        shape = CutCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CutCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.08f))
                    .border(0.8.dp, badgeColor.copy(alpha = 0.35f), CutCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    color = Color(0xFFB0BDCC),
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = badge,
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(badgeColor.copy(alpha = 0.11f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    color = badgeColor,
                    fontFamily = RobotoMonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.48f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
