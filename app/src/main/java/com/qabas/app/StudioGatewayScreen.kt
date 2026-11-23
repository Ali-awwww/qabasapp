package com.qabas.app

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
 * 🛸 استوديو خادم المسلم الموحّد لصناعة المحتوى (Unified Creative Production Studio)
 * تم تنظيم الاستوديو في 3 فئات رئيسية: فيديوهات، صور، صوت.
 * مع دعم نظام Bring-Your-Own-Key (BYOK) للمنصات الخارجية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioGatewayScreen(
    onBack: () -> Unit,
    onNavigateToMasterStudio: () -> Unit = {},
    onNavigateToMediaBrowser: () -> Unit = {},
    onNavigateToAiStudio: () -> Unit,
    onNavigateToProEditor: () -> Unit,
    onNavigateToMoneyPrinter: () -> Unit = {},
    onNavigateToPhotoStudio: () -> Unit = {},
    onNavigateToVoiceStudio: () -> Unit = {},
    onNavigateToYouTubeStudio: () -> Unit = {},
    onNavigateToTeleprompter: () -> Unit = {},
    onNavigateToStyleCloner: () -> Unit = {},
    onNavigateToHadithStudio: () -> Unit = {},
    onNavigateToStats: () -> Unit,
    onNavigateToAiStudioAgent: () -> Unit = {},
    onNavigateToStudioAgent: () -> Unit = {},
    onNavigateToMyKeys: () -> Unit = {},
    onNavigateToStudioLibrary: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val theme = QabasTheme.current
    BackHandler { onBack() }

    val context = LocalContext.current
    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    // (12.3) أُزيل حوار ApiKeyManagerDialog من البوابة — زر المفتاح يوجّه إلى «مفاتيحي».

    // فئات الاستوديو الموحدة (3 فئات)
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = remember {
        listOf(
            "🎬 الفيديوهات (Higgsfield)" to "video",
            "🎨 الصور (Leonardo)" to "images",
            "🎙️ الصوت (VoiceStudio)" to "audio"
        )
    }

    Scaffold(
        containerColor = theme.background,
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
                                "استوديو خادم المسلم الموحّد",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                "UNIFIED STUDIO HUB // BYOK_CONFIGURABLE",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = theme.textPrimary)
                    }
                },
                actions = {
                    // (12.3) حُذف ApiKeyManagerDialog المكرر (Leonardo/Higgsfield فقط)
                    // من البوابة؛ زر المفتاح يوجّه إلى «مفاتيحي» (MY_KEYS) المدخل الوحيد.
                    IconButton(onClick = onNavigateToMyKeys) {
                        Icon(Icons.Default.VpnKey, contentDescription = "مفاتيحي", tint = primaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background.copy(alpha = 0.95f))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        listOf(theme.cardSurface, theme.background)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Hero Banner
            UnifiedHeroStudioCard(
                onMasterStudio = onNavigateToMasterStudio,
                onQuickVideo = onNavigateToAiStudio,
                onMoneyPrinter = onNavigateToMoneyPrinter,
                primaryColor = primaryColor,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Category Selector (3 Main Hubs)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEachIndexed { index, (label, _) ->
                    val isSelected = selectedCategoryIndex == index
                    Surface(
                        onClick = { selectedCategoryIndex = index },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) primaryColor else theme.secondarySurface.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (isSelected) primaryColor else theme.secondarySurface)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label.substringBefore(" ("),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                color = if (isSelected) DeepSlate else theme.textPrimary.copy(alpha = 0.8f),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val currentFilter = categories[selectedCategoryIndex].second

            // Render Content based on Hub
            when (currentFilter) {
                "video" -> {
                    SectionHeader("صناعة ومونتاج الفيديو (Higgsfield AI)", "VIDEO_HUB", "🎬")
                    
                    StudioFeatureCard(
                        title = "توليد وتحريك الفيديو (Higgsfield AI) 🛸",
                        description = "حوّل نصوصك وصورك إلى مقاطع سينمائية دعوية بلمسة احترافية.",
                        badge = "CINEMATIC AI",
                        badgeColor = theme.primary,
                        icon = Icons.Default.MovieFilter,
                        onClick = onNavigateToAiStudio
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "أدوات دخل المبدعين ⚡",
                        description = "أتمتة إنتاج الريلز والشورتس بضغطة زر واحدة (سكربت + صوت + لقطات).",
                        badge = "AUTOMATION",
                        badgeColor = theme.success,
                        icon = Icons.Default.Bolt,
                        onClick = onNavigateToMoneyPrinter
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "المحرر الاحترافي (Pro Editor) ✂️",
                        description = "تحكم كامل في التايم لاين، المونتاج، وإضافة التأثيرات السينمائية.",
                        badge = "PRO TOOLS",
                        badgeColor = theme.primary,
                        icon = Icons.Default.ContentCut,
                        onClick = onNavigateToProEditor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "استوديو يوتيوب والانتشار 📺",
                        description = "إدارة السلاسل، عناوين SEO، وإحصائيات الوصول لجمهورك.",
                        badge = "GROWTH",
                        badgeColor = theme.destructive,
                        icon = Icons.Default.SmartDisplay,
                        onClick = onNavigateToYouTubeStudio
                    )
                }
                "images" -> {
                    SectionHeader("استوديو الصور والتصاميم (Leonardo.ai)", "IMAGE_HUB", "🎨")
                    
                    StudioFeatureCard(
                        title = "توليد الصور الاحترافية (Leonardo.ai) 🖼️",
                        description = "توليد صور فائقة الجودة وبوسترات دعوية باستخدام محرك Leonardo Vision.",
                        badge = "LEONARDO VISION",
                        badgeColor = theme.warning,
                        icon = Icons.Default.Image,
                        onClick = onNavigateToPhotoStudio
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "استوديو بطاقات الآيات والأحاديث 📜",
                        description = "تصميم بطاقات فخمة وجاهزة للنشر للآيات والأحاديث النبوية.",
                        badge = "HADITH ART",
                        badgeColor = primaryColor,
                        icon = Icons.Default.MenuBook,
                        onClick = onNavigateToHadithStudio
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "مستودع الوسائط المركزي 📦",
                        description = "إدارة وعرض جميع الصور والفيديوهات المولدة والمحفوظة.",
                        badge = "STORAGE",
                        badgeColor = theme.success,
                        icon = Icons.Default.PermMedia,
                        onClick = onNavigateToMediaBrowser
                    )
                }
                "audio" -> {
                    SectionHeader("هندسة الصوت والتعليق (VoiceStudio)", "AUDIO_HUB", "🎙️")
                    
                    StudioFeatureCard(
                        title = "استوديو الأصوات والدبلجة (VoiceStudio) 🎙️",
                        description = "توليد تعليق صوتي فخم واستنساخ النبرات بدقة عالية.",
                        badge = "VOICE ENGINE",
                        badgeColor = theme.accent,
                        icon = Icons.Default.GraphicEq,
                        onClick = onNavigateToVoiceStudio
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StudioFeatureCard(
                        title = "ملقن الكاميرا الذكي (Teleprompter) 📹",
                        description = "تصوير فيديوهاتك مع قراءة النصوص المتزامنة تلقائياً.",
                        badge = "VIDEO PRODUCTION",
                        badgeColor = theme.success,
                        icon = Icons.Default.Videocam,
                        onClick = onNavigateToTeleprompter
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // وكيل الاستوديو الموحّد (الدفعة 4) — ظاهر دائماً فوق كل الفئات
            SectionHeader("وكيل الاستوديو الموحّد", "STUDIO_AGENT_HUB", "🤖")
            StudioFeatureCard(
                title = "وكيل الاستوديو 💬",
                description = "محادثة واحدة تصنع الريلز: فكرة ← خطة ← سكربت ← صور ← صوت ← فيديو ← نشر.",
                badge = "STUDIO AGENT",
                badgeColor = theme.primary,
                icon = Icons.Default.Chat,
                onClick = onNavigateToStudioAgent
            )
            Spacer(modifier = Modifier.height(12.dp))
            StudioFeatureCard(
                title = "مفاتيحي 🔑",
                description = "كل مفاتيح المزودين في شاشة واحدة — فحص حي قبل الحفظ وعرض مقنّع دائماً.",
                badge = "BYOK KEYS",
                badgeColor = theme.warning,
                icon = Icons.Default.VpnKey,
                onClick = onNavigateToMyKeys
            )
            Spacer(modifier = Modifier.height(12.dp))
            StudioFeatureCard(
                title = "مكتبة المشاريع 📚",
                description = "مشاريعك وطابور مهام الفيديو وسجل التوليدات — تابع من حيث توقفت.",
                badge = "LIBRARY",
                badgeColor = theme.success,
                icon = Icons.Default.VideoLibrary,
                onClick = onNavigateToStudioLibrary
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Wisdom Note
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = theme.cardSurface.copy(alpha = 0.6f),
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
    }
}

@Composable
fun ApiKeyManagerDialog(onDismiss: () -> Unit) {
    val theme = QabasTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val primaryColor = qabasPrimary()
    val textMutedColor = theme.textSecondary
    val cyanInfoColor = theme.primary

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
            backgroundColor = theme.cardSurface.copy(alpha = 0.98f),
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
                                color = theme.textPrimary,
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
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // Educational / Honesty Explainer Card
                GlassmorphicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = theme.secondarySurface.copy(alpha = 0.7f),
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
                                color = theme.textPrimary,
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
                    backgroundColor = theme.cardSurface.copy(alpha = 0.85f),
                    borderColor = if (leonardoKey.isNotBlank()) primaryColor.copy(alpha = 0.5f) else theme.textPrimary.copy(alpha = 0.1f),
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
                                        color = theme.textPrimary,
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
                                Text("📋 لصق تلقائي", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 10.sp)
                            }

                            // Live Connection Test Button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isTestingLeonardo = true
                                        try {
                                            leonardoTestResult = LeonardoImageService.testConnection(context, leonardoKey)
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
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
                                Text("⚡ فحص الاتصال", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 10.sp)
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
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "مسح", tint = theme.textTertiary, modifier = Modifier.size(18.dp))
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
                                        tint = theme.textTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = theme.textPrimary,
                                unfocusedTextColor = theme.textPrimary,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = theme.textPrimary.copy(alpha = 0.15f)
                            )
                        )

                        // Live Test Feedback
                        leonardoTestResult?.let { result ->
                            val isOk = result.first
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isOk) EmeraldGreen.copy(alpha = 0.15f) else theme.destructive.copy(alpha = 0.15f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isOk) EmeraldGreen else theme.destructive,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    result.second,
                                    color = theme.textPrimary,
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
                    backgroundColor = theme.cardSurface.copy(alpha = 0.85f),
                    borderColor = if (higgsfieldKey.isNotBlank() || isHiggsfieldConfigured) primaryColor.copy(alpha = 0.5f) else theme.textPrimary.copy(alpha = 0.1f),
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
                                        color = theme.textPrimary,
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
                                Text("📋 لصق تلقائي", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 10.sp)
                            }

                            // Live Connection Test Button
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isTestingHiggsfield = true
                                        try {
                                            higgsfieldTestResult = HiggsfieldVideoService.testConnection(context)
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
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
                                Text("⚡ حالة الربط", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 10.sp)
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
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "مسح", tint = theme.textTertiary, modifier = Modifier.size(18.dp))
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
                                        tint = theme.textTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = theme.textPrimary,
                                unfocusedTextColor = theme.textPrimary,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = theme.textPrimary.copy(alpha = 0.15f)
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
                                    .background(if (isOk) EmeraldGreen.copy(alpha = 0.15f) else theme.destructive.copy(alpha = 0.15f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isOk) EmeraldGreen else theme.destructive,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    result.second,
                                    color = theme.textPrimary,
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
                            // (r17) حفظ بعد فحص حي فقط: زر الحفظ السابق كان
                            // يكتب مفتاح Leonardo دون أي فحص، وزر «فحص
                            // الاتصال» اختياري لا يمنع الحفظ. الفحص المعروض
                            // الناجح لنفس القيمة يُقبل دون إعادة فحص.
                            val leonardoClean = sanitizeKey(leonardoKey)
                            val leonardoOk = if (leonardoClean.isBlank()) {
                                true
                            } else if (leonardoTestResult?.first == true) {
                                true
                            } else {
                                val res = ApiKeyValidator.validateKey(context, "leonardo", leonardoClean)
                                leonardoTestResult = res.isValid to res.summary
                                res.isValid
                            }
                            if (!leonardoOk) {
                                Toast.makeText(context, "لم يُحفظ مفتاح Leonardo: ${leonardoTestResult?.second ?: "فشل الفحص الحي"}", Toast.LENGTH_LONG).show()
                                isSavingHiggsfield = false
                                return@launch
                            }
                            val keyToSave = sanitizeKey(higgsfieldKey)
                            val higgsfieldSaved = keyToSave.isBlank() || PlatformGatewayClient.saveProviderKeySuspend("higgsfield", keyToSave)
                            if (higgsfieldSaved) {
                                ApiKeyManager.saveLeonardoKey(context, leonardoKey)
                                if (keyToSave.isNotBlank()) isHiggsfieldConfigured = true
                                higgsfieldKey = ""
                                // (r17) الرسالة السابقة كانت تدّعي أن مفتاح
                                // Leonardo حُفظ «على الخادم الآمن» بينما هو
                                // محلي مشفّر فقط؛ مفتاح Higgsfield وحده هو
                                // ما يذهب إلى البوابة.
                                Toast.makeText(
                                    context,
                                    "تم حفظ مفتاح Leonardo محلياً (مشفّر)" +
                                        if (keyToSave.isNotBlank()) " ومفتاح Higgsfield على الخادم الآمن ✓" else " ✓",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "تعذر حفظ مفتاح Higgsfield. تحقق من صلاحية حساب المطور واتصال بوابة خادم المسلم.", Toast.LENGTH_LONG).show()
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
                    Text("إلغاء", color = theme.textTertiary, fontFamily = CairoFont)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, codeTag: String, emoji: String = "✦") {
    val theme = QabasTheme.current
    val primaryColor = qabasPrimary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "STATUS // HUB_READY",
                    color = primaryColor.copy(alpha = 0.5f),
                    fontFamily = RobotoMonoFont,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun UnifiedHeroStudioCard(
    onMasterStudio: () -> Unit = {},
    onQuickVideo: () -> Unit,
    onMoneyPrinter: () -> Unit,
    primaryColor: Color,
    accentColor: Color
) {
    val theme = QabasTheme.current
    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = theme.background.copy(alpha = 0.85f),
        borderColor = primaryColor.copy(alpha = 0.35f),
        shape = CutCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "صناعة عمل دعوي متقن وشامل ✦",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "نظام متكامل لإنتاج المحتوى الدعوي بضغطة زر واحدة (سيناريو + صوت + صور + SEO).",
                color = theme.textSecondary,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onMasterStudio,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = CutCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "إطلاق قمرة الإنتاج المتقن الشامل",
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
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
    val theme = QabasTheme.current
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = theme.cardSurface.copy(alpha = 0.45f),
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
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    color = theme.textTertiary,
                    fontFamily = CairoFont,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = theme.textPrimary.copy(alpha = 0.15f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
