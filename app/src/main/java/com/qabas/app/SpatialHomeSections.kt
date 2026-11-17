package com.qabas.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.theme.*

@Composable
internal fun SpatialStudiosSector(
    onStudioGateway: () -> Unit,
    onReels: () -> Unit = {},
    onProEditor: () -> Unit,
    onMoneyPrinterTurbo: () -> Unit,
    onVoiceStudio: () -> Unit,
    onPhotoStudio: () -> Unit,
    onYouTubeStudio: () -> Unit,
    onTeleprompter: () -> Unit,
    onVideoStyleCloner: () -> Unit,
    onStyleSelection: () -> Unit,
    onMediaBrowser: () -> Unit = {},
    isDeveloper: Boolean = false
) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hero Banner: Unified Studio Entry
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clickable { onStudioGateway() },
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF101827),
            shadowElevation = if (theme.isDark) 0.dp else 2.dp
        ) {
            Box {
                AsyncImage(
                    model = R.drawable.spatial_hud_gateway_refined_1790877953352,
                    contentDescription = "استوديو قبس الموحد",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.55f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.25f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(16.dp)
                ) {
                    Surface(
                        color = theme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "الاستوديو الرئيسي",
                            color = theme.onPrimary,
                            fontFamily = CairoFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "استوديو التحف الموحد",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    Text(
                        text = "ابدأ مشروعك الدعوي بالذكاء الاصطناعي المتكامل",
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(22.dp)
                )
            }
        }

        SpatialHubCard(
            title = "ريلز ومجتمع قبس الإيماني",
            subtitle = "مشاهدة ونشر مقاطع الريلز القصيرة والتفاعل مع صناع الأثر",
            icon = Icons.Default.SlowMotionVideo,
            badge = "ريلز 9:16",
            color = theme.primary,
            modifier = Modifier.fillMaxWidth(),
            onClick = onReels
        )

        SpatialHubCard(
            title = "استوديو التحف الموحد & Higgsfield AI",
            subtitle = "توليد فيديوهات دعوية متكاملة مع تعليق صوتي ومؤثرات",
            icon = Icons.Default.MovieFilter,
            badge = "توليد فيديو",
            color = Color(0xFFFF3B30),
            modifier = Modifier.fillMaxWidth(),
            onClick = onStudioGateway
        )

        SpatialHubCard(
            title = "مستودع الوسائط وسحابة Supabase",
            subtitle = "تصفح وإدارة كل الملفات المرفوعة والمولّدة بالذكاء",
            icon = Icons.Default.PermMedia,
            badge = "الوسائط",
            color = theme.success,
            modifier = Modifier.fillMaxWidth(),
            onClick = onMediaBrowser
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "MoneyPrinter Turbo",
                subtitle = "أتمتة إنتاج ريلز وشورتس 9:16 بضغطة زر",
                icon = Icons.Default.Bolt,
                badge = "سريع",
                color = Color(0xFF0A84FF),
                modifier = Modifier.weight(1f),
                onClick = onMoneyPrinterTurbo
            )
            SpatialHubCard(
                title = "VoiceStudio",
                subtitle = "استنساخ الأصوات ودبلجة الفيديوهات",
                icon = Icons.Default.GraphicEq,
                badge = "أصوات",
                color = Color(0xFFAF52DE),
                modifier = Modifier.weight(1f),
                onClick = onVoiceStudio
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "استوديو قبس الشامل",
                subtitle = "البوابة المركزية ومسارات الإنتاج الذكية",
                icon = Icons.Default.AutoAwesome,
                badge = "الرئيسي",
                color = theme.primary,
                modifier = Modifier.weight(1f),
                onClick = onStudioGateway
            )
            SpatialHubCard(
                title = "المحرر اليدوي الاحترافي",
                subtitle = "تايم لاين، مسارات، ومونتاج دقيق",
                icon = Icons.Default.MovieFilter,
                badge = "Pro",
                color = Color(0xFF32ADE6),
                modifier = Modifier.weight(1f),
                onClick = onProEditor
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "استوديو الصور والمصغرات",
                subtitle = "تصميم أغلفة وبوسترات 4K",
                icon = Icons.Default.Image,
                badge = "صور",
                color = Color(0xFFFF9500),
                modifier = Modifier.weight(1f),
                onClick = onPhotoStudio
            )
            SpatialHubCard(
                title = "ملقن الكاميرا الذكي",
                subtitle = "تسجيل الفيديو مع قراءة النصوص",
                icon = Icons.Default.Videocam,
                badge = "ملقن",
                color = theme.success,
                modifier = Modifier.weight(1f),
                onClick = onTeleprompter
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "مستنسخ الأنماط البصرية",
                subtitle = "استخلاص الأسلوب والمونتاج من الفيديوهات",
                icon = Icons.Default.Style,
                badge = "أنماط",
                color = Color(0xFF5856D6),
                modifier = Modifier.weight(1f),
                onClick = onVideoStyleCloner
            )
            SpatialHubCard(
                title = "استوديو يوتيوب والشورتس",
                subtitle = "توليد العناوين، الفصول، والـ SEO",
                icon = Icons.Default.SmartDisplay,
                badge = "YouTube",
                color = Color(0xFFFF3B30),
                modifier = Modifier.weight(1f),
                onClick = onYouTubeStudio
            )
        }

        SpatialHubCard(
            title = "مكتبة الأنماط السينمائية",
            subtitle = "اختيار وتخصيص نمط المونتاج والهوية البصرية",
            icon = Icons.Default.Palette,
            badge = "الأنماط",
            color = Color(0xFF06B6D4),
            modifier = Modifier.fillMaxWidth(),
            onClick = onStyleSelection
        )
    }
}

@Composable
internal fun SpatialIslamicSuiteSector(
    onHadithStudio: () -> Unit,
    onIslamicLibrary: () -> Unit,
    onAzkar: () -> Unit,
    onPrayerTimes: () -> Unit,
    onScholarBiographies: () -> Unit,
    onKnowledgeHub: () -> Unit,
    isDeveloper: Boolean = false
) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isDeveloper) {
            SpatialHubCard(
                title = "استوديو الحديث الشريف",
                subtitle = "صحيح البخاري ومسلم وبطاقات 4K",
                icon = Icons.Default.AutoStories,
                badge = "الأحاديث الصحيحة",
                color = theme.primary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onHadithStudio
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "الأذكار وحصن المسلم",
                subtitle = "أذكار الصباح والمساء والسبحة الذكية",
                icon = Icons.Default.Favorite,
                badge = "حصن المسلم",
                color = Color(0xFFFF2D55),
                modifier = Modifier.weight(1f),
                onClick = onAzkar
            )
            SpatialHubCard(
                title = "مواقيت الصلاة والقبلة",
                subtitle = "حساب فلكي دقيق وبوصلة القبلة",
                icon = Icons.Default.Explore,
                badge = "المواقيت",
                color = Color(0xFF0A84FF),
                modifier = Modifier.weight(1f),
                onClick = onPrayerTimes
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "المكتبة الإسلامية الكبرى",
                subtitle = "أمهات كتب التفسير، الحديث، والعقيدة",
                icon = Icons.Default.LocalLibrary,
                badge = "كتب محققة",
                color = Color(0xFFFF9500),
                modifier = Modifier.weight(1f),
                onClick = onIslamicLibrary
            )
            SpatialHubCard(
                title = "سيرة الأكابر وتراجم الأئمة",
                subtitle = "سير أئمة السلف والعلماء والمحدثين",
                icon = Icons.Default.School,
                badge = "التراجم",
                color = Color(0xFF5856D6),
                modifier = Modifier.weight(1f),
                onClick = onScholarBiographies
            )
        }

        SpatialHubCard(
            title = "منارة المعرفة وتزكية النفس",
            subtitle = "مقالات إيمانية وبناء الوعي الدعوي والتزكية",
            icon = Icons.Default.Lightbulb,
            badge = "تزكية ومعرفة",
            color = Color(0xFF14B8A6),
            modifier = Modifier.fillMaxWidth(),
            onClick = onKnowledgeHub
        )
    }
}

@Composable
internal fun SpatialAiEnginesSector(
    onAiAssistant: () -> Unit,
    onContentGuard: () -> Unit,
    onAudioLibrary: () -> Unit,
    onNavigateToTasteProfile: () -> Unit,
    onLeaderboard: () -> Unit,
    onRewards: () -> Unit,
    onPremiumUpgrade: () -> Unit,
    onViewApiDocs: () -> Unit,
    onDeveloperDashboard: () -> Unit,
    isAdmin: Boolean,
    isDeveloper: Boolean = false
) {
    val theme = QabasTheme.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isDeveloper) {
                SpatialHubCard(
                    title = "مستشار الذكاء الاصطناعي",
                    subtitle = "توليد سيناريوهات وأفكار إخراجية مبتكرة",
                    icon = Icons.Default.Psychology,
                    badge = "AI",
                    color = theme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onAiAssistant
                )
            }
            SpatialHubCard(
                title = "حارس المحتوى الشرعي",
                subtitle = "تدقيق وفلترة إسلامية لضمان السلامة",
                icon = Icons.Default.Shield,
                badge = "تدقيق",
                color = theme.success,
                modifier = Modifier.weight(1f),
                onClick = onContentGuard
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "مكتبة الصوتيات والتلاوات",
                subtitle = "تلاوات خاشعة ومؤثرات صوتية مباحة",
                icon = Icons.Default.GraphicEq,
                badge = "صوتيات",
                color = Color(0xFFFF2D55),
                modifier = Modifier.weight(1f),
                onClick = onAudioLibrary
            )
            SpatialHubCard(
                title = "الذوق الفني والأسلوب",
                subtitle = "تخصيص الهوية ونمطك المفضل في المونتاج",
                icon = Icons.Default.Tune,
                badge = "الذوق",
                color = Color(0xFF5856D6),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasteProfile
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "لوحة الشرف وميدان الحسنات",
                subtitle = "رصيد النقاط والتنافس المبارك في نشر الخير",
                icon = Icons.Default.EmojiEvents,
                badge = "الترتيب",
                color = Color(0xFFFF9500),
                modifier = Modifier.weight(1f),
                onClick = onLeaderboard
            )
            SpatialHubCard(
                title = "سوق الجوائز والمكافآت",
                subtitle = "استبدال النقاط والحسنات بمكافآت حصرية",
                icon = Icons.Default.CardGiftcard,
                badge = "المكافآت",
                color = Color(0xFF14B8A6),
                modifier = Modifier.weight(1f),
                onClick = onRewards
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialHubCard(
                title = "ترقية الحساب المميز VIP",
                subtitle = "تصدير 4K وخوادم سريعة وميزات حصرية",
                icon = Icons.Default.WorkspacePremium,
                badge = "VIP",
                color = theme.primary,
                modifier = Modifier.weight(1f),
                onClick = onPremiumUpgrade
            )
            SpatialHubCard(
                title = "توثيق الواجهات البرمجية",
                subtitle = "دليل الربط البرمجي للمطورين API",
                icon = Icons.Default.Code,
                badge = "API",
                color = Color(0xFF32ADE6),
                modifier = Modifier.weight(1f),
                onClick = onViewApiDocs
            )
        }

        SpatialHubCard(
            title = "بوابة قبس والمستودع الرسمي",
            subtitle = "الموقع الرسمي: تحميل التحديثات والدروس العلمية",
            icon = Icons.Default.Language,
            badge = "الموقع",
            color = Color(0xFF0A84FF),
            modifier = Modifier.fillMaxWidth(),
            onClick = { QabasWebConfig.openWebsite(context) }
        )

        if (isAdmin) {
            SpatialHubCard(
                title = "لوحة تحكم المطورين والمفاتيح",
                subtitle = "تشخيص الخوادم، مفاتيح الذكاء، ومراقبة النظام",
                icon = Icons.Default.Terminal,
                badge = "المطور",
                color = theme.destructive,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDeveloperDashboard
            )
        }
    }
}

@Composable
internal fun SpatialProjectsSector(
    recentProjects: List<ProjectService.Project>,
    onOpenProject: (ProjectService.Project) -> Unit,
    onViewProjects: () -> Unit,
    onNewProject: () -> Unit
) {
    val theme = QabasTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (recentProjects.isEmpty()) {
            AppleCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "لا توجد مشاريع سابقة بعد",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "ابدأ بصناعة أول فيديو دعوي الآن عبر الاستوديو",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ApplePrimaryButton(
                        text = "إنشاء مشروع جديد",
                        icon = Icons.Default.Add,
                        onClick = onNewProject
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "أحدث المشاريع (${recentProjects.size})",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TextButton(onClick = onViewProjects) {
                    Text(
                        text = "عرض الكل",
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            recentProjects.take(4).forEach { proj ->
                SpatialProjectItem(
                    project = proj,
                    onClick = { onOpenProject(proj) }
                )
            }

            OutlinedButton(
                onClick = onViewProjects,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, theme.separator)
            ) {
                Text(
                    text = "إدارة كل المشاريع (${recentProjects.size})",
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
internal fun SpatialHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = modifier,
        onClick = {
            AppSoundManager.playClick()
            onClick()
        }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }

                Surface(
                    color = color.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        color = color,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = theme.textSecondary,
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun SpatialWarpCreationHub(
    quickPromptText: String,
    onQuickPromptChange: (String) -> Unit,
    isRecordingVoice: Boolean,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onIgnite: () -> Unit,
    isDeveloper: Boolean = false
) {
    if (!isDeveloper) return
    val theme = QabasTheme.current

    AppleCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "محرك التوليد السريع",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "حوّل أي فكرة أو صوت إلى فيديو متكامل",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            OutlinedTextField(
                value = quickPromptText,
                onValueChange = onQuickPromptChange,
                placeholder = {
                    Text(
                        text = if (isRecordingVoice) "جاري التسجيل الصوتي... تحدث بفكرتك" else "اكتب فكرة الفيديو، موضوع دعوي، أو آية وحديث...",
                        color = theme.textSecondary,
                        fontSize = 12.5.sp,
                        fontFamily = CairoFont
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = theme.primary, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    IconButton(
                        onClick = { if (isRecordingVoice) onStopVoice() else onStartVoice() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecordingVoice) Icons.Default.StopCircle else Icons.Default.Mic,
                            contentDescription = "تسجيل صوتي",
                            tint = if (isRecordingVoice) theme.destructive else theme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = CairoFont,
                    fontSize = 13.5.sp,
                    color = theme.textPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = theme.secondarySurface.copy(alpha = 0.45f),
                    unfocusedContainerColor = theme.secondarySurface.copy(alpha = 0.45f),
                    focusedBorderColor = theme.primary,
                    unfocusedBorderColor = theme.separator.copy(alpha = 0.6f)
                ),
                maxLines = 3
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val presets = listOf(
                    "ريلز دعوي خاطف",
                    "قصة قرآنية مؤثرة",
                    "شرح حديث نبوي",
                    "سلسلة رمضانية",
                    "حكمة وتأمل قصير"
                )
                items(presets) { preset ->
                    Surface(
                        onClick = { onQuickPromptChange("صناعة فيديو عن: $preset") },
                        shape = RoundedCornerShape(10.dp),
                        color = theme.secondarySurface.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = preset,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            ApplePrimaryButton(
                text = if (quickPromptText.isNotBlank()) "إطلاق المعالجة" else "فتح استوديو الإنتاج الشامل",
                icon = Icons.Default.PlayArrow,
                onClick = onIgnite
            )
        }
    }
}
