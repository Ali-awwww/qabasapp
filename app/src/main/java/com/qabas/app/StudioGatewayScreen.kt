package com.qabas.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*

/**
 * 🛸 استوديو قبس الموحّد لصناعة المحتوى (Unified Creative Production Studio)
 * بيئة إنتاجية متكاملة تجمع كل أدوات الذكاء الاصطناعي، المونتاج الاحترافي، هندسة الصوت،
 * وتصميم البوسترات ونشر السلاسل في مركز قيادة واحد فائق السلاسة.
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
    bottomBar: @Composable () -> Unit = {}
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val userEmail = remember { prefs.getString("user_email", "") ?: "" }
    val userType = remember { prefs.getString("user_type", "") ?: "" }
    var isDeveloper by remember {
        mutableStateOf(
            prefs.getBoolean("is_admin", false) ||
            CloudServices.isOwnerAccount(userEmail) ||
            userType == "مطور" ||
            userType == "خاص" ||
            userType == "admin" ||
            userType == "developer"
        )
    }

    if (!isDeveloper) {
        StudioMaintenanceView(
            onBack = onBack,
            onDeveloperAuthenticated = {
                isDeveloper = true
            },
            bottomBar = bottomBar
        )
        return
    }

    val primaryColor = qabasPrimary()
    val accentColor = qabasAccent()

    // فئات الاستوديو للتصفية السريعة
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = remember {
        listOf(
            "🌟 الكل" to "all",
            "✨ العمل المتقن" to "master",
            "🗂️ مستودع الوسائط" to "media",
            "🎬 صناعة الفيديو" to "video",
            "🎨 التصميم والبوسترات" to "visuals",
            "🎙️ الصوتيات والدبلجة" to "audio",
            "🚀 النشر والانتشار" to "growth"
        )
    }

    Scaffold(
        containerColor = Color(0xFF030712), // Deeper dark
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
                                Translator.tr("استوديو قبس الموحّد"),
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                "COMMAND CENTER v2.0 // SYSTEM ACTIVE",
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
            // HUD Developer Mode Banner (Active for developers only)
            Surface(
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.9f),
                border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(primaryColor, Color(0xFF38BDF8)))),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = Translator.tr("وضع المطور نشط 👑 // ROOT_ACCESS"),
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = Translator.tr("الاستوديو معلّق للمستخدمين ومتاح لك بصفتك مطوراً"),
                                color = primaryColor.copy(alpha = 0.85f),
                                fontFamily = CairoFont,
                                fontSize = 10.sp
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            isDeveloper = false
                        }
                    ) {
                        Text(
                            text = Translator.tr("معاينة كـ مستخدم 🔒"),
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Hero Banner: Quick Production Launcher
            UnifiedHeroStudioCard(
                onMasterStudio = onNavigateToMasterStudio,
                onQuickVideo = onNavigateToAiStudio,
                onMoneyPrinter = onNavigateToMoneyPrinter,
                primaryColor = primaryColor,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Category Selector Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories.size) { index ->
                    val isSelected = selectedCategoryIndex == index
                    val (label, _) = categories[index]
                    Surface(
                        onClick = { selectedCategoryIndex = index },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) primaryColor else Color(0xFF1E293B).copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (isSelected) primaryColor else Color(0xFF1E293B)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = label,
                                color = if (isSelected) DeepSlate else Color.White.copy(alpha = 0.8f),
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Autonomous AI Agent Studio Feature Card
            StudioFeatureCard(
                title = "استوديو الوكيل الذكي (AI Studio Agent) 🛸",
                description = "بيئة تطوير مستقلة قائمة على إعطاء الأوامر باللغة الطبيعية — يفحص الكود، يبني الميزات، ويشغل البناء السحابي تماماً كمنصة Google AI Studio.",
                badge = "AUTONOMOUS DEV",
                badgeColor = Color(0xFF00E5FF),
                icon = Icons.Default.SmartToy,
                onClick = onNavigateToAiStudioAgent
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Studio Tools Grid / List based on selected category
            val currentFilter = categories[selectedCategoryIndex].second

            // Masterpiece All-in-One Pillar
            if (currentFilter == "all" || currentFilter == "master") {
                SectionHeader("قمرة الإنتاج المتقن الشامل", "FLAGSHIP MASTER ENGINE", "✨")

                SectionHeroBanner(
                    title = "الإنتاج المتقن الشامل",
                    description = "نظام متكامل يدمج السيناريو، الصوت، الصور، والمونتاج في تدفق واحد فائق السرعة.",
                    // Using generated resource
                    imageRes = R.drawable.hud_master_engine_banner_1790878207544,
                    fallbackEmoji = "🌟",
                    accentColor = primaryColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                StudioFeatureCard(
                    title = "استوديو العمل المتقن الشامل ✦",
                    description = "دمج فوري لكل ميزات قبس: سيناريو + تعليق صوتي فخم + رسوم 8K FLUX + كابشنز حركية + بوستر وغلاف + حزمة SEO للنشر بضغطة زر.",
                    badge = "MASTER WORKFLOW",
                    badgeColor = primaryColor,
                    icon = Icons.Default.Stars,
                    onClick = onNavigateToMasterStudio
                )

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Media Browser & Supabase Storage Pillar
            if (currentFilter == "all" || currentFilter == "media") {
                SectionHeader("مستودع وسائط قبس", "CENTRAL STORAGE & ASSET NEXUS", "🗂️")

                SectionHeroBanner(
                    title = "مستودع الوسائط المركزي",
                    description = "إدارة وعرض جميع الملفات المرفوعة والمولّدة بالذكاء (صور 8K، صوتيات، فيديوهات ريلز) مع المزامنة السحابية المباشرة.",
                    fallbackEmoji = "📦",
                    accentColor = Color(0xFF10B981)
                )

                Spacer(modifier = Modifier.height(14.dp))

                StudioFeatureCard(
                    title = "مستودع الوسائط المركزي (Supabase & AI) 📦",
                    description = "إدارة وعرض جميع الملفات المرفوعة والمولّدة بالذكاء (صور 8K، صوتيات، فيديوهات ريلز) مع المزامنة السحابية المباشرة.",
                    badge = "CENTRAL STORAGE",
                    badgeColor = Color(0xFF10B981),
                    icon = Icons.Default.PermMedia,
                    onClick = onNavigateToMediaBrowser
                )

                Spacer(modifier = Modifier.height(18.dp))
            }

            // 1. Video Production Section
            if (currentFilter == "all" || currentFilter == "video") {
                SectionHeader("أدوات صناعة ومونتاج الفيديو", "VIDEO ENGINES", "🎬")

                SectionHeroBanner(
                    title = "مصنع الفيديو الذكي",
                    description = "أتمتة إنتاج الريلز والشورتس بضغطة زر مع المحرر التفاعلي المتقدم.",
                    // Using generated resource
                    imageRes = R.drawable.hud_video_factory_banner_1790878221069,
                    fallbackEmoji = "⚡",
                    accentColor = Color(0xFF00F2FE)
                )

                Spacer(modifier = Modifier.height(14.dp))

                StudioFeatureCard(
                    title = "MoneyPrinter Turbo ⚡",
                    description = "أتمتة إنتاج الريلز والشورتس 9:16 بضغطة زر واحدة (سكربت + صوت Edge-TTS + لقطات + كابشنز حركية).",
                    badge = "TURBO ZERO-KEY",
                    badgeColor = Color(0xFF00F2FE),
                    icon = Icons.Default.Bolt,
                    onClick = onNavigateToMoneyPrinter
                )

                Spacer(modifier = Modifier.height(10.dp))

                StudioFeatureCard(
                    title = "المحرر التفاعلي والتايم لاين المتقدم ✂️",
                    description = "تحكم متعدد المسارات، قص السكتات (AI Jump Cut)، فلاتر سينمائية، وتصدير فائق الدقة.",
                    badge = "SPATIAL TIMELINE",
                    badgeColor = Color(0xFF38BDF8),
                    icon = Icons.Default.MovieFilter,
                    onClick = onNavigateToProEditor
                )

                Spacer(modifier = Modifier.height(10.dp))

                StudioFeatureCard(
                    title = "مستنسخ الأنماط البصرية (Style Cloner) 🧬",
                    description = "تحليل الأسلوب الإخراجي لأي فيديو ناجح وتطبيقه تلقائياً على نصوصك وأفكارك.",
                    badge = "AI CLONER",
                    badgeColor = Color(0xFFA855F7),
                    icon = Icons.Default.Style,
                    onClick = onNavigateToStyleCloner
                )

                Spacer(modifier = Modifier.height(18.dp))
            }

            // 2. Visuals & Posters Section
            if (currentFilter == "all" || currentFilter == "visuals") {
                SectionHeader("استوديو التصميم والبوسترات", "VISUAL & POSTER DESIGN", "🎨")

                SectionHeroBanner(
                    title = "التصميم والبوسترات",
                    description = "توليد خلفيات وبوسترات فائقة الجودة بدقة 8K مع زخارف هندسية إسلامية.",
                    fallbackEmoji = "🖼️",
                    accentColor = Color(0xFFF59E0B)
                )

                Spacer(modifier = Modifier.height(14.dp))

                StudioFeatureCard(
                    title = "استوديو الصور والبوسترات (FLUX.1 8K) 🖼️",
                    description = "توليد خلفيات وبوسترات فائقة الجودة بدون مفاتيح، تصميم أغلفة دعوية، وزخارف هندسية إسلامية.",
                    badge = "FLUX.1 ZERO-API",
                    badgeColor = Color(0xFFF59E0B),
                    icon = Icons.Default.Image,
                    onClick = onNavigateToPhotoStudio
                )

                Spacer(modifier = Modifier.height(10.dp))

                StudioFeatureCard(
                    title = "استوديو بطاقات الآيات والأحاديث 📜",
                    description = "بطاقات حديث شريف وآيات قرآنية بتنسيق بصري فخم جاهزة للنشر على إنستغرام وتويتر.",
                    badge = "HADITH STUDIO",
                    badgeColor = primaryColor,
                    icon = Icons.Default.MenuBook,
                    onClick = onNavigateToHadithStudio
                )

                Spacer(modifier = Modifier.height(18.dp))
            }

            // 3. Audio & Dubbing Section
            if (currentFilter == "all" || currentFilter == "audio") {
                SectionHeader("هندسة الصوت والدبلجة", "VOICE & ACOUSTIC LAB", "🎙️")

                SectionHeroBanner(
                    title = "مختبر الصوتيات",
                    description = "توليد تعليق صوتي فخم واستنساخ النبرات بدقة عالية.",
                    // Using generated resource
                    imageRes = R.drawable.hud_audio_lab_banner_1790878245000,
                    fallbackEmoji = "🔊",
                    accentColor = Color(0xFFEC4899)
                )

                Spacer(modifier = Modifier.height(14.dp))

                StudioFeatureCard(
                    title = "استوديو الأصوات والدبلجة (VoiceStudio) 🎙️",
                    description = "توليد تعليق صوتي فخم بدون مفاتيح (Edge-TTS و Kokoro)، استنساخ النبرات، ودبلجة الفيديو.",
                    badge = "VOICE ENGINE",
                    badgeColor = Color(0xFFEC4899),
                    icon = Icons.Default.GraphicEq,
                    onClick = onNavigateToVoiceStudio
                )

                Spacer(modifier = Modifier.height(10.dp))

                StudioFeatureCard(
                    title = "ملقن الكاميرا الذكي (Smart Teleprompter) 📹",
                    description = "تصوير فيديوهاتك مع قراءة النصوص المتزامنة تلقائياً مع وتيرة صوتك.",
                    badge = "TELEPROMPTER",
                    badgeColor = Color(0xFF10B981),
                    icon = Icons.Default.Videocam,
                    onClick = onNavigateToTeleprompter
                )

                Spacer(modifier = Modifier.height(18.dp))
            }

            // 4. Growth & Publishing Section
            if (currentFilter == "all" || currentFilter == "growth") {
                SectionHeader("النشر والانتشار", "GROWTH & VIRAL SEO", "🚀")

                SectionHeroBanner(
                    title = "النشر والانتشار",
                    description = "أدوات متكاملة لتحسين محركات البحث وزيادة الوصول الدعوي.",
                    fallbackEmoji = "📈",
                    accentColor = Color(0xFFEF4444)
                )

                Spacer(modifier = Modifier.height(14.dp))

                StudioFeatureCard(
                    title = "استوديو يوتيوب والشورتس الآلي 📺",
                    description = "إنتاج سلاسل يومية من 30 حلقة، توليد عناوين جذابة، وسوم SEO، ومصغرات يوتيوب.",
                    badge = "AUTO SERIES & SEO",
                    badgeColor = Color(0xFFEF4444),
                    icon = Icons.Default.SmartDisplay,
                    onClick = onNavigateToYouTubeStudio
                )

                Spacer(modifier = Modifier.height(10.dp))

                StudioFeatureCard(
                    title = "لوحة إحصائيات الانتشار والأثر 📊",
                    description = "قياس انتشار المحتوى، تحليل تفاعل الجمهور، واقتراحات استراتيجية لزيادة الوصول الدعوي.",
                    badge = "ANALYTICS",
                    badgeColor = Color(0xFF22C55E),
                    icon = Icons.Default.BarChart,
                    onClick = onNavigateToStats
                )

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Bottom Wisdom Note
            GlassmorphicSurface(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                borderColor = primaryColor.copy(alpha = 0.25f),
                shape = CutCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "«قيمة المرء ما يُحسنُ صنعه».. منظومة قبس تمنحك استوديو سينمائي مستقل 100% لنشر الخير والأثر المستدام.",
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, codeTag: String, emoji: String = "✦") {
    val primaryColor = qabasPrimary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Technical Vertical HUD Accent with Emoji
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(primaryColor)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    lineHeight = 15.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SUBSYSTEM // ACCESS_GRANTED",
                        color = primaryColor.copy(alpha = 0.5f),
                        fontFamily = RobotoMonoFont,
                        fontSize = 7.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(3.dp).background(primaryColor.copy(alpha = 0.3f), CircleShape))
                }
            }
        }
        
        Surface(
            color = Color.Transparent,
            border = BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.25f)),
            shape = CutCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(4.dp).background(primaryColor.copy(alpha = 0.6f), CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = codeTag,
                    color = primaryColor.copy(alpha = 0.7f),
                    fontFamily = RobotoMonoFont,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SectionHeroBanner(
    title: String,
    description: String,
    imageRes: Int? = null,
    fallbackEmoji: String = "🛸",
    accentColor: Color = qabasPrimary()
) {
    val context = LocalContext.current
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.6f),
        borderColor = accentColor.copy(alpha = 0.25f),
        shape = CutCornerShape(14.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Technical Background Pattern
            Canvas(modifier = Modifier.matchParentSize()) {
                val step = 16.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt()) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.03f),
                        start = Offset(x.toFloat(), 0f),
                        end = Offset(x.toFloat(), size.height),
                        strokeWidth = 1f
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageRes != null) {
                        Image(
                            painter = painterResource(id = imageRes),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(fallbackEmoji, fontSize = 30.sp)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = CutCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            "SUBSYSTEM_ACTIVE",
                            color = accentColor,
                            fontFamily = RobotoMonoFont,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFF030712).copy(alpha = 0.85f),
        borderColor = primaryColor.copy(alpha = 0.35f),
        shape = CutCornerShape(16.dp)
    ) {
        // Technical Background Grid Pattern (Simulated)
        Canvas(modifier = Modifier.matchParentSize()) {
            val step = 20.dp.toPx()
            for (x in 0..size.width.toInt() step step.toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.03f),
                    start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f),
                    end = androidx.compose.ui.geometry.Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step step.toInt()) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.03f),
                    start = androidx.compose.ui.geometry.Offset(0f, y.toFloat()),
                    end = androidx.compose.ui.geometry.Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = primaryColor.copy(alpha = 0.1f),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "CORE NEXUS // SYNC_ACTIVE",
                        color = primaryColor,
                        fontFamily = RobotoMonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ZERO-API ENGINE",
                        color = Color(0xFF10B981),
                        fontFamily = RobotoMonoFont,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "صناعة عمل دعوي متقن وشامل ✦",
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "أدخل فكرتك لإنتاج الحزمة الكاملة فورياً (سيناريو + تعليق صوتي + صور 8K + كابشنز + بوستر + SEO) بضغطة زر واحدة.",
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onMasterStudio,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = CutCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "إطلاق قمرة الإنتاج المتقن الشامل",
                    color = DeepSlate,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onMoneyPrinter,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = CutCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF111827).copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00F2FE), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("أتمتة ريلز", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onQuickVideo,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = CutCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF111827).copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الاستوديو الذكي", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
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
    GlassmorphicSurface(
        modifier = Modifier
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
                    .size(50.dp)
                    .clip(CutCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.08f))
                    .border(0.8.dp, badgeColor.copy(alpha = 0.35f), CutCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp)
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
                        fontSize = 13.5.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = badgeColor.copy(alpha = 0.1f),
                        shape = CutCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = badge,
                            color = badgeColor,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 7.5.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    color = Color(0xFF64748B),
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
