package com.qabas.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.theme.*

@Composable
fun QabasNavigationDrawerContent(
    isAdmin: Boolean,
    isGuest: Boolean = false,
    isDrawerOpen: Boolean = false,
    onCloseDrawer: () -> Unit,
    onNavigateTo: (() -> Unit) -> Unit,
    onStudioGateway: () -> Unit,
    onReels: () -> Unit = {},
    onProEditor: () -> Unit,
    onMoneyPrinterTurbo: () -> Unit = {},
    onVoiceStudio: () -> Unit = {},
    onPhotoStudio: () -> Unit,
    onTeleprompter: () -> Unit,
    onVideoStyleCloner: () -> Unit,
    onYouTubeStudio: () -> Unit,
    onViewProjects: () -> Unit,
    onMishkatHub: () -> Unit,
    onMushafReader: () -> Unit = {},
    onIslamicLibrary: () -> Unit,
    onAzkar: () -> Unit,
    onScholarBiographies: () -> Unit,
    onPrayerTimes: () -> Unit,
    onAudioLibrary: () -> Unit,
    onAiAssistant: () -> Unit,
    onContentGuard: () -> Unit,
    onLeaderboard: () -> Unit,
    onNavigateToTasteProfile: () -> Unit,
    onPremiumUpgrade: () -> Unit,
    onViewProfile: () -> Unit,
    onViewSettings: () -> Unit,
    onViewApiDocs: () -> Unit,
    onDeveloperDashboard: () -> Unit,
    onNavigateToRules: () -> Unit = {},
    onAiStudioAgent: () -> Unit = {},
    onViewDownloads: () -> Unit = {}
) {
    val theme = QabasTheme.current
    // ق103: كان remember عادياً فالتدوير داخل تصنيف يعيد قائمة
    // التصنيفات — والدرج نفسه ينجو من التدوير (DrawerState محفوظ).
    var selectedCategory by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen) selectedCategory = null
    }
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Apple HIG Sidebar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = theme.primary.copy(alpha = if (theme.isDark) 0.22f else 0.12f),
                    border = BorderStroke(0.8.dp, theme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "شعار خادم المسلم",
                            tint = theme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "خادم المسلم",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = if (isAdmin) "الإدارة والمطور" else "استوديو الإنتاج الدعوي",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onCloseDrawer,
                modifier = Modifier.size(48.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = theme.secondarySurface,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق القائمة",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Status Pill
        // ق103: كانت «حساب المبدع نشط» تظهر لكل غير-مطور بمن فيهم
        // الضيف بلا حساب — ادعاء كاذب؛ للضيف شارة صادقة بلون التحذير.
        val pillColor = when {
            isAdmin -> theme.destructive
            isGuest -> theme.warning
            else -> theme.success
        }
        val pillText = when {
            isAdmin -> "وضع المطور والإدارة مفعّل"
            isGuest -> "تصفح كضيف"
            else -> "حساب المبدع نشط"
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            color = pillColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(0.5.dp, pillColor.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(pillColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pillText,
                    color = pillColor,
                    fontFamily = CairoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = theme.separator.copy(alpha = 0.45f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (selectedCategory == null) {
                Text(
                    text = "أقسام التطبيق",
                    color = theme.textSecondary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                )

                AppleCard {
                    QabasDrawerCategoryItem(
                        title = "استوديوهات الإنتاج",
                        subtitle = "الإنتاج والتحرير المرئي",
                        icon = Icons.Outlined.VideoLibrary,
                        iconTint = Color(0xFF0A84FF),
                        onClick = { selectedCategory = 0 }
                    )
                    AppleInsetDivider()
                    QabasDrawerCategoryItem(
                        title = "العالم الإسلامي",
                        subtitle = "المكتبة والعلوم الشرعية",
                        icon = Icons.Outlined.MenuBook,
                        iconTint = theme.primary,
                        onClick = { selectedCategory = 1 }
                    )
                    AppleInsetDivider()
                    QabasDrawerCategoryItem(
                        title = "الذكاء الاصطناعي",
                        subtitle = "الأدوات والمساعدات الذكية",
                        icon = Icons.Outlined.AutoAwesome,
                        iconTint = Color(0xFF5856D6),
                        onClick = { selectedCategory = 2 }
                    )
                    AppleInsetDivider()
                    QabasDrawerCategoryItem(
                        title = "حسابي",
                        subtitle = "الملف الشخصي والاشتراك والإعدادات",
                        icon = Icons.Outlined.Person,
                        iconTint = Color(0xFF14B8A6),
                        onClick = { selectedCategory = 3 }
                    )
                    AppleInsetDivider()
                    QabasDrawerCategoryItem(
                        title = "المطورون والسياسات",
                        subtitle = "التوثيق والقواعد وإدارة النظام",
                        icon = Icons.Outlined.Code,
                        iconTint = Color(0xFFF59E0B),
                        onClick = { selectedCategory = 4 }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "﴿ ادْعُ إِلَىٰ سَبِيلِ رَبِّكَ بِالْحِكْمَةِ وَالْمَوْعِظَةِ الْحَسَنَةِ ﴾",
                        color = theme.primary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontFamily = AmiriFont,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "خادم المسلم • استوديو الإنتاج الإسلامي",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = NotoSansFont
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedCategory = null },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة إلى التصنيفات",
                            tint = theme.primary
                        )
                    }
                    Text(
                        text = when (selectedCategory) {
                            0 -> "استوديوهات الإنتاج"
                            1 -> "العالم الإسلامي"
                            2 -> "الذكاء الاصطناعي"
                            3 -> "حسابي"
                            else -> "المطورون والسياسات"
                        },
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                AppleCard {
                    when (selectedCategory) {
                        0 -> {
                            QabasDrawerItem(
                                title = "مركز الإنتاج المتكامل",
                                subtitle = "البوابة المركزية ومسارات الإنتاج الذكية",
                                icon = Icons.Default.AutoAwesome,
                                iconTint = theme.primary,
                                badgeText = "الرئيسي",
                                badgeColor = theme.primary,
                                onClick = { onNavigateTo(onStudioGateway) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "استوديو الوكيل الذكي",
                                subtitle = "بيئة تطوير قائمة على الأوامر باللغة الطبيعية",
                                icon = Icons.Default.SmartToy,
                                iconTint = Color(0xFF0A84FF),
                                badgeText = "AI",
                                badgeColor = Color(0xFF0A84FF),
                                onClick = { onNavigateTo(onAiStudioAgent) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "مركز التنزيلات والنماذج المحلية",
                                subtitle = "إدارة نماذج GGUF، ريلز، وصوتيات الجهاز",
                                icon = Icons.Default.CloudDownload,
                                iconTint = Color(0xFF32ADE6),
                                badgeText = "محلي",
                                badgeColor = Color(0xFF32ADE6),
                                onClick = { onNavigateTo(onViewDownloads) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "المحرر اليدوي الاحترافي",
                                subtitle = "تايم لاين، مسارات، ومونتاج دقيق",
                                icon = Icons.Default.MovieFilter,
                                iconTint = Color(0xFF38BDF8),
                                badgeText = "PRO",
                                badgeColor = Color(0xFF38BDF8),
                                onClick = { onNavigateTo(onProEditor) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "ريلز خادم المسلم",
                                subtitle = "مجتمع الإلهام ومقاطع الفيديو القصيرة",
                                icon = Icons.Default.SlowMotionVideo,
                                iconTint = theme.primary,
                                // ق103: شارة «جديد» الدائمة أُزيلت — القسم
                                // ناضج (جولات ق9/ق42/ق57/ق67/ق76/ق81/ق86/ق91)
                                // فالشارة ادعاء لا ينتهي.
                                onClick = { onNavigateTo(onReels) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "مصنع الريلز السريع",
                                subtitle = "أتمتة إنتاج شورتس 9:16 مع التعليق العصبي",
                                icon = Icons.Default.Bolt,
                                iconTint = Color(0xFF06B6D4),
                                badgeText = "TURBO",
                                badgeColor = Color(0xFF06B6D4),
                                onClick = { onNavigateTo(onMoneyPrinterTurbo) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "استوديو الأصوات والدبلجة",
                                subtitle = "أنماط صوتية Edge-Neural ودبلجة الفيديوهات",
                                icon = Icons.Default.GraphicEq,
                                iconTint = Color(0xFFAF52DE),
                                badgeText = "VOICE",
                                badgeColor = Color(0xFFAF52DE),
                                onClick = { onNavigateTo(onVoiceStudio) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "استوديو الصور والمصغرات",
                                subtitle = "تصميم أغلفة ومصغرات جذابة للفيديوهات",
                                icon = Icons.Default.AddPhotoAlternate,
                                iconTint = Color(0xFFFF2D55),
                                onClick = { onNavigateTo(onPhotoStudio) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "ملقن الكاميرا الذكي",
                                subtitle = "تسجيل الفيديو مع قراءة النصوص والتحكم بالسرعة",
                                icon = Icons.Default.Videocam,
                                iconTint = theme.success,
                                onClick = { onNavigateTo(onTeleprompter) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "مستنسخ الأنماط البصرية",
                                subtitle = "تحليل وتكرار أساليب المونتاج الناجحة",
                                icon = Icons.Default.Palette,
                                iconTint = Color(0xFF5856D6),
                                onClick = { onNavigateTo(onVideoStyleCloner) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "استوديو يوتيوب والشورتس",
                                subtitle = "أدوات نشر وتحسين لقنوات الفيديو والريلز",
                                icon = Icons.Default.Subscriptions,
                                iconTint = Color(0xFFFF3B30),
                                onClick = { onNavigateTo(onYouTubeStudio) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "مشاريعي ومسوداتي",
                                subtitle = "جميع الأعمال السابقة والمسودات المحفوظة",
                                icon = Icons.Default.FolderOpen,
                                iconTint = Color(0xFFFF9500),
                                onClick = { onNavigateTo(onViewProjects) }
                            )
                        }
                        1 -> {
                            // ق103: قارئ المصحف — القسم الأول في التطبيق —
                            // كان غائباً تماماً عن فهرس الأقسام (grep: صفر
                            // «مصحف» في هذا الملف)؛ يُفتح باستئناف آخر قراءة.
                            QabasDrawerItem(
                                title = "المصحف الشريف",
                                subtitle = "القراءة والتلاوة مع الاستئناف التلقائي",
                                icon = Icons.Default.AutoStories,
                                iconTint = theme.primary,
                                onClick = { onNavigateTo(onMushafReader) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "مِشكاة الهُدى العامة",
                                subtitle = "واحة النور والقرآن والزاد الإيماني",
                                icon = Icons.Default.Mosque,
                                iconTint = theme.primary,
                                onClick = { onNavigateTo(onMishkatHub) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "المكتبة الإسلامية",
                                subtitle = "أمهات كتب التفسير، الحديث، وعقيدة أهل السنة",
                                icon = Icons.Default.LibraryBooks,
                                iconTint = Color(0xFFFF9500),
                                onClick = { onNavigateTo(onIslamicLibrary) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "الأذكار وحصن المسلم",
                                subtitle = "أذكار الصباح والمساء، الرقية، والسبحة الذكية",
                                icon = Icons.Default.Favorite,
                                iconTint = Color(0xFFFF2D55),
                                onClick = { onNavigateTo(onAzkar) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "سيرة الأكابر وتراجم الأئمة",
                                subtitle = "مواقف صمود علماء وسلف الأمة المباركة",
                                icon = Icons.Default.PersonSearch,
                                iconTint = Color(0xFF32ADE6),
                                onClick = { onNavigateTo(onScholarBiographies) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "الأذان ومواقيت الصلاة",
                                subtitle = "مواقيت دقيقة، أصوات الأذان، وغرفة التحكم",
                                icon = Icons.Default.NotificationsActive,
                                iconTint = Color(0xFF0A84FF),
                                onClick = { onNavigateTo(onPrayerTimes) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "مكتبة الصوتيات الدعوية",
                                subtitle = "خطب، محاضرات، ومؤثرات صوتية مباحة",
                                icon = Icons.Default.Audiotrack,
                                iconTint = Color(0xFF5856D6),
                                onClick = { onNavigateTo(onAudioLibrary) }
                            )
                        }
                        2 -> {
                            QabasDrawerItem(
                                title = "مستشار الذكاء الاصطناعي",
                                subtitle = "توليد نصوص دعوية وأفكار إخراجية مبتكرة",
                                icon = Icons.Default.SmartToy,
                                iconTint = Color(0xFF0A84FF),
                                badgeText = "AI",
                                badgeColor = Color(0xFF0A84FF),
                                onClick = { onNavigateTo(onAiAssistant) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "حارس المحتوى الشرعي",
                                subtitle = "تدقيق ومراجعة المحتوى لضمان السلامة الشرعية",
                                icon = Icons.Default.VerifiedUser,
                                iconTint = theme.success,
                                onClick = { onNavigateTo(onContentGuard) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "لوحة الشرف وميدان الحسنات",
                                subtitle = "رصيد النقاط والتنافس المبارك في نشر الخير",
                                icon = Icons.Default.EmojiEvents,
                                iconTint = theme.primary,
                                onClick = { onNavigateTo(onLeaderboard) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "الذوق الفني والأسلوب",
                                subtitle = "تخصيص الهوية البصرية ونمطك المفضل في المونتاج",
                                icon = Icons.Default.Tune,
                                iconTint = Color(0xFF5856D6),
                                onClick = { onNavigateTo(onNavigateToTasteProfile) }
                            )
                        }
                        3 -> {
                            QabasDrawerItem(
                                title = "ترقية الحساب المميز (VIP)",
                                subtitle = "تصدير فائق الدقة 4K، خوادم سريعة، وميزات حصرية",
                                icon = Icons.Default.WorkspacePremium,
                                iconTint = theme.primary,
                                badgeText = "VIP",
                                badgeColor = theme.primary,
                                onClick = { onNavigateTo(onPremiumUpgrade) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "الملف الشخصي",
                                subtitle = "إدارة الحساب، الإحصائيات، وسجل الإنجازات",
                                icon = Icons.Default.AccountCircle,
                                iconTint = theme.accent,
                                onClick = { onNavigateTo(onViewProfile) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "الإعدادات العامة",
                                subtitle = "المظهر، التنبيهات، مفاتيح الـ API، واللغة",
                                icon = Icons.Default.Settings,
                                iconTint = Color(0xFF8E8E93),
                                onClick = { onNavigateTo(onViewSettings) }
                            )
                        }
                        4 -> {
                            QabasDrawerItem(
                                title = "توثيق الواجهات البرمجية (API)",
                                subtitle = "دليل الربط البرمجي للمطورين والمنصات الخارجية",
                                icon = Icons.Default.Code,
                                iconTint = Color(0xFF0A84FF),
                                onClick = { onNavigateTo(onViewApiDocs) }
                            )
                            AppleInsetDivider()
                            QabasDrawerItem(
                                title = "القواعد والسياسات الموحدة",
                                subtitle = "شروط الاستخدام، سياسة الخصوصية، وقواعد المجتمع",
                                icon = Icons.Default.Gavel,
                                iconTint = Color(0xFFFF9500),
                                onClick = { onNavigateTo(onNavigateToRules) }
                            )
                            if (isAdmin) {
                                AppleInsetDivider()
                                QabasDrawerItem(
                                    title = "لوحة تحكم المطورين",
                                    subtitle = "تشخيص الخوادم، مفاتيح الذكاء، ومراقبة النظام",
                                    icon = Icons.Default.AdminPanelSettings,
                                    iconTint = theme.destructive,
                                    badgeText = "إدارة",
                                    badgeColor = theme.destructive,
                                    onClick = { onNavigateTo(onDeveloperDashboard) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QabasDrawerCategoryItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(iconTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = theme.textSecondary.copy(alpha = 0.65f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun QabasDrawerItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    badgeText: String? = null,
    badgeColor: Color = QabasTheme.current.primary,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeText != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFont,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = theme.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}
