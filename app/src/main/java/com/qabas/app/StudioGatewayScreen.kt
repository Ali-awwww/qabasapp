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
                        description = "توليد تعليق صوتي فخم بأنماط Edge-Neural مع ضبط النبرة والسرعة.",
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
