package com.qabas.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.changelog.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val FULL_OFFICIAL_PRIVACY_POLICY = """
ميثاق الخصوصية والضوابط الشرعية لتطبيق خادم المسلم (Muslim AI Assistant)
الإصدار المعتمد: v${BuildConfig.VERSION_NAME}

بسم الله الرحمن الرحيم
«إِنَّ السَّمْعَ وَالْبَصَرَ وَالْفُؤَادَ كُلُّ أُولَٰئِكَ كَانَ عَنْهُ مَسْئُولًا» [الإسراء: 36]

١. النزاهة والمحتوى الشرعي النقي:
يلتزم تطبيق خادم المسلم بضمان خلو كافة التصاميم والتلاوات والأصوات والنصوص من أي مخالفات شرعية أو عقدية، وتخلو المقاطع المنتجة تماماً من المعازف والآلات الموسيقية المحرمة.

٢. التخزين المحلي وحفظ الأمانات (Offline-First):
تُخزن جميع المشاريع والتسجيلات الخاصة بك محلياً داخل جهازك في قاعدة بيانات التطبيق. لا نقوم برفع أو قراءة ملفاتك الشخصية دون طلبك، والمحتوى الأساسي (القرآن والأذكار ومواقيت الصلاة) يعمل دون اتصال بالإنترنت، بينما تحتاج مزايا الذكاء الاصطناعي والريلز والتحديثات إلى اتصال.

٣. عدم بيع أو مشاركة البيانات:
لا نبيع بياناتك أو نشاطك لأي طرف ثالث، وتطبيق خادم المسلم خالٍ تماماً من الإعلانات التجارية وأدوات التتبع الربحية. تُستخدم بيانات الاستخدام (مثل مشاهدات الشاشات والتقييمات التي ترسلها) لتحسين التطبيق فقط.

٤. أمان المعالجة والذكاء الاصطناعي المسؤول:
تتم معالجة نصوص الآيات والأحاديث النبوية والتفاسير وفق منهج أهل السنة والجماعة المعتمد، ولا يتم توليد أي محتوى يمس ثوابت العقيدة الإسلامية.

٥. التحكم الكامل وحذف الحساب والملفات:
يملك المستخدم السيطرة الكاملة على بياناته، مع إمكانية مسح الذاكرة المؤقتة، تصفير المشاريع، أو حذف كافة الملفات بلمسة واحدة من داخل التطبيق. كما يمكنه حذف حسابه نهائياً من إعدادات الحساب (بعد تصدير نسخة احتياطية إن أراد): يُحذف حساب المصادقة وملفه الشخصي وبياناته السحابية، وتُسجَّل خروج جلسته.

٦. التواصل والدعم الفني المباشر:
للتواصل المباشر مع فريق التطوير أو الانضمام لمجتمع خادم المسلم الرسمي على واتساب:
${QabasWebConfig.DEFAULT_COMMUNITY_GROUP_URL}
""".trimIndent()

/**
 * شاشة مخصصة وفاخرة لـ:
 * 1. معلومات التطبيق والإصدار الرسمي (About Us & App Version)
 * 2. الضوابط الشرعية وسياسة الخصوصية مع روابط التصفح والنسخ المباشرة (Privacy Policy Link & Viewer)
 * 3. سجل التحديثات المستخرج مباشرة من قاعدة البيانات المحلية (Changelog fetched from Room Database)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUsAndVersionScreen(
    onBack: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    // قاعدة البيانات وسجل التحديثات
    val db = remember(context) { AppDatabase.getDatabase(context) }
    val changelogRepo = remember(db) { ChangelogRepository(db.changelogDao()) }
    val changelogsState by changelogRepo.allChangelogsFlow.collectAsState(initial = emptyList())

    // التأكد من تعبئة البيانات الافتراضية لقاعدة البيانات
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            changelogRepo.ensureDefaultChangelogs()
        }
    }

    // حالات الحوارات
    var showFullPrivacyDialog by remember { mutableStateOf(false) }
    var showDeveloperGateDialog by remember { mutableStateOf(false) }
    var showEditPrivacyUrlDialog by remember { mutableStateOf(false) }
    var customPrivacyUrlInput by remember { mutableStateOf("") }
    var devTapCount by remember { mutableIntStateOf(0) }
    val isDevMode = remember(context) { AdminGuard.isDashboardAccessAllowed(context) }
    var customPrivacyUrl by remember { mutableStateOf(prefs.getString("custom_privacy_url", "") ?: "") }

    // حالة فحص التحديثات
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateResultMsg by remember { mutableStateOf<String?>(null) }

    // حالات تصفية وترتيب سجل التحديثات الذكي
    var changelogSearchQuery by remember { mutableStateOf("") }
    var changelogSelectedType by remember { mutableStateOf(ChangelogUpdateType.ALL) }
    var changelogSelectedVersion by remember { mutableStateOf<String?>(null) }
    var changelogSortOrder by remember { mutableStateOf(ChangelogSortOrder.DATE_DESC) }

    val availableVersions = remember(changelogsState) {
        changelogsState.map { it.versionName }.distinct()
    }

    val typeCounts = remember(changelogsState) {
        val map = mutableMapOf<ChangelogUpdateType, Int>()
        map[ChangelogUpdateType.ALL] = changelogsState.size
        map[ChangelogUpdateType.FEATURE] = changelogsState.count { it.detectContainedTypes().contains(ChangelogUpdateType.FEATURE) }
        map[ChangelogUpdateType.SECURITY] = changelogsState.count { it.detectContainedTypes().contains(ChangelogUpdateType.SECURITY) }
        map[ChangelogUpdateType.BUG_FIX] = changelogsState.count { it.detectContainedTypes().contains(ChangelogUpdateType.BUG_FIX) }
        map
    }

    val filteredChangelogs = remember(changelogsState, changelogSearchQuery, changelogSelectedType, changelogSelectedVersion, changelogSortOrder) {
        changelogsState
            .filter { item ->
                val matchesVersion = changelogSelectedVersion == null || item.versionName.equals(changelogSelectedVersion, ignoreCase = true)
                val matchesType = when (changelogSelectedType) {
                    ChangelogUpdateType.ALL -> true
                    else -> item.detectContainedTypes().contains(changelogSelectedType)
                }
                val matchesQuery = if (changelogSearchQuery.isBlank()) {
                    true
                } else {
                    val q = changelogSearchQuery.trim().lowercase()
                    item.versionName.lowercase().contains(q) ||
                    item.versionCode.toString().contains(q) ||
                    item.releaseDate.lowercase().contains(q) ||
                    item.title.lowercase().contains(q) ||
                    item.categoryBadge.lowercase().contains(q) ||
                    item.highlights.lowercase().contains(q)
                }
                matchesVersion && matchesType && matchesQuery
            }
            .let { list ->
                when (changelogSortOrder) {
                    ChangelogSortOrder.DATE_DESC -> list.sortedWith(compareByDescending<ChangelogEntity> { it.versionCode }.thenByDescending { it.timestamp })
                    ChangelogSortOrder.DATE_ASC -> list.sortedWith(compareBy<ChangelogEntity> { it.versionCode }.thenBy { it.timestamp })
                }
            }
    }

    // رابط المجموعة الرسمية من مصدر واحد (QabasWebConfig) — كان هنا وفي نص
    // السياسة وفي نموذج التواصل ثلاث نسخ مختلفة من معاملات التتبع لنفس الدعوة.
    val officialWhatsAppGroupUrl = QabasWebConfig.getOfficialGroupUrl(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PresetPrimary.copy(alpha = 0.15f))
                                .border(1.dp, PresetPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "عن خادم المسلم وسجل التحديثات",
                                color = PresetPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                color = qabasTextSecondary(),
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp
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
                    IconButton(onClick = {
                        val shareText = "✨ تطبيق «خادم المسلم | Muslim AI Assistant» v${BuildConfig.VERSION_NAME}\nاستوديو صناعة المحتوى الإسلامي والقرآن الكريم بالذكاء الاصطناعي.\n💬 انضم لمجتمع خادم المسلم الرسمي على واتساب:\n$officialWhatsAppGroupUrl"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة معلومات التطبيق"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = PresetPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = qabasBackground())
            )
        },
        bottomBar = bottomBar,
        containerColor = qabasBackground()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1️⃣ بطاقة الهيدر الفاخرة — هوية التطبيق والإصدار (Hero Version & Mission)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .luxuryCardStyle(shapeRadius = 22.dp, borderAlpha = 0.45f, glowElevation = 6.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(theme.secondarySurface, theme.cardSurface, theme.background)
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // الشعار النوراني المتوهج مع النبض
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(PresetPrimary.copy(alpha = 0.15f))
                                    .border(2.dp, Brush.radialGradient(listOf(PresetPrimary, PresetAccent, Color.Transparent)), CircleShape)
                                    .clickable {
                                        devTapCount++
                                        if (devTapCount >= 7 && !isDevMode) {
                                            showDeveloperGateDialog = true
                                            devTapCount = 0
                                        } else if (isDevMode) {
                                            Toast.makeText(context, "وضع المطور مفعّل بالفعل 🛠️", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "شعار خادم المسلم",
                                    tint = PresetPrimary,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "مَنظُومَة خادم المسلم الإِسْلَامِيَّة",
                                color = PresetPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            Text(
                                text = "Muslim AI Assistant & Islamic Knowledge Hub",
                                color = qabasTextSecondary(),
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // شارة الإصدار الرسمية
                            Surface(
                                color = PresetPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "الإصدار v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                        color = PresetPrimary,
                                        fontFamily = NotoSansFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // الآية الكريمة
                            Surface(
                                color = theme.background.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, theme.secondarySurface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "﴿ إِذْ رَأَىٰ نَارًا فَقَالَ لِأَهْلِهِ امْكُثُوا إِنِّي آنَسْتُ نَارًا لَّعَلِّي آتِيكُم مِّنْهَا بِقَبَسٍ ﴾",
                                        color = theme.textPrimary,
                                        fontFamily = AmiriFont,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "سورة طه • آية 10",
                                        color = PresetAccent,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "خادم المسلم هو استوديو متكامل في جيبك لصناعة المحتوى الدعوي والإسلامي الهادف بالذكاء الاصطناعي، يجمع بين تلاوة وتدبر القرآن الكريم، الصحيحين، الإخراج السينمائي، والتعليق الصوتي الصافي.",
                                color = qabasTextPrimary(),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 📜 سجل التغييرات الكامل (Full Detailed History Entry)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.45f)
                        .clickable { onNavigateToHistory() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PresetPrimary.copy(alpha = 0.15f))
                                    .border(1.2.dp, PresetPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "سجل التحديثات التفصيلي",
                                    color = PresetPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "استعرض تاريخ تطور التطبيق والمزايا الجديدة",
                                    color = qabasTextSecondary(),
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = PresetPrimary.copy(alpha = 0.7f))
                    }
                }
            }

            // 🌐 بطاقة الموقع الرسمي والمستودع السحابي لمنظومة خادم المسلم (Official Web Hub & Cloud Repository)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.45f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PresetPrimary.copy(alpha = 0.15f))
                                        .border(1.2.dp, PresetPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "بوابة خادم المسلم والمستودع السحابي",
                                        color = PresetPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        "الموقع الرسمي لخادم المسلم",
                                        color = qabasTextSecondary(),
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = theme.background.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, theme.secondarySurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = QabasWebConfig.OFFICIAL_WEBSITE_URL,
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // زر واحد فقط: مسار /lectures على الموقع يعيد 404 (الموقع صفحة واحدة
                        // بلا مسارات فرعية حية)، فحُذف زر «الدروس والدرر» الميت بدل عرضه.
                        Button(
                            onClick = { QabasWebConfig.openWebsite(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("زيارة الموقع 🚀", color = DeepSlate, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(QabasWebConfig.OFFICIAL_WEBSITE_URL))
                                Toast.makeText(context, "تم نسخ رابط الموقع الرسمي للحافظة 📋", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, PresetPrimary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ رابط الموقع", color = PresetPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 💬 بطاقة مجموعة ومجتمع خادم المسلم الرسمي (Official Community & Discussion Group)
            item {
                val groupUrl = remember { QabasWebConfig.getOfficialGroupUrl(context) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.45f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(theme.success.copy(alpha = 0.18f))
                                        .border(1.2.dp, theme.success.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Groups, contentDescription = null, tint = theme.success, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "مجموعة ومجتمع خادم المسلم الرسمي",
                                        color = theme.success,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        "ملتقى المبدعين وطلاب العلم لتبادل الأفكار والدعم",
                                        color = qabasTextSecondary(),
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            Surface(
                                color = theme.success.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, theme.success.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "مجتمع تفاعلي 💬",
                                    color = theme.success,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = theme.background.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, theme.secondarySurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = theme.success, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = groupUrl,
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { QabasWebConfig.openCommunityGroup(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.success),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.3f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("الانضمام للمجموعة 💬", color = DeepSlate, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(groupUrl))
                                    Toast.makeText(context, "تم نسخ رابط المجموعة للحافظة 📋", Toast.LENGTH_SHORT).show()
                                },
                                border = BorderStroke(1.dp, theme.success),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(0.9f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = theme.success, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ الرابط", color = theme.success, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2️⃣ بطاقة سياسة الخصوصية والضوابط الشرعية (Privacy Policy & Islamic Integrity)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.35f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.success.copy(alpha = 0.15f))
                                        .border(1.dp, theme.success.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Gavel, contentDescription = null, tint = theme.success, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "الضوابط الشرعية وسياسة الخصوصية",
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "ميثاق النزاهة وحفظ الأمانات",
                                        color = qabasTextSecondary(),
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Surface(
                                color = theme.success.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "مدمجة أوفلاين 🛡️",
                                    color = theme.success,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // توضيح للمستخدم بأن السياسة مدمجة ولا تحتاج لموقع
                        Surface(
                            color = theme.background.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, theme.secondarySurface)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.success, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "سياسة الخصوصية مدمجة بالكامل داخل التطبيق وتُقرأ دون الحاجة لأي موقع ويب أو إنترنت. مشاريعك وملفاتك محفوظة محلياً على جهازك، ولا تُرفع إلى السحابة إلا ما تختر مزامنته أو نشره بنفسك.",
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // زر قراءة السياسة كاملة
                            Button(
                                onClick = { showFullPrivacyDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.success),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.3f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("قراءة الميثاق والسياسة", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // زر مشاركة نص الميثاق
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, FULL_OFFICIAL_PRIVACY_POLICY)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "مشاركة ميثاق وسياسة خصوصية خادم المسلم"))
                                },
                                border = BorderStroke(1.dp, theme.success),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = theme.success, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة النص", color = theme.success, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // زر نسخ النص كاملاً
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(FULL_OFFICIAL_PRIVACY_POLICY))
                                    Toast.makeText(context, "تم نسخ ميثاق وسياسة الخصوصية كاملاً للحافظة 📋", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ السياسة", tint = PresetPrimary, modifier = Modifier.size(18.dp))
                            }
                        }

                        // إذا كان هناك رابط مخصص أضافه المطور
                        if (customPrivacyUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "رابط إلكتروني مخصص: $customPrivacyUrl",
                                    color = qabasTextSecondary(),
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = {
                                        QabasLinkOpener.openUrl(context, customPrivacyUrl, "سياسة الخصوصية")
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("فتح 🌐", color = PresetPrimary, fontSize = 11.sp, fontFamily = CairoFont)
                                }
                            }
                        }

                        // خيار إضافي للمطور
                        if (isDevMode) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        customPrivacyUrlInput = customPrivacyUrl
                                        showEditPrivacyUrlDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = PresetAccent, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (customPrivacyUrl.isBlank()) "إضافة رابط ويب لمتجر Google Play ⚙️" else "تعديل رابط الويب ⚙️",
                                        color = PresetAccent,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 🌟 بطاقة آراء ومراجعات المجتمع الحقيقية (Community Reviews & Ratings System - Supabase Live)
            item {
                CommunityReviewsCard()
            }

            // 3️⃣ سجل التحديثات المستخرج من قاعدة البيانات (Changelog from Room DB)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PresetPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "سجل التحديثات والإصدارات (من قاعدة البيانات)",
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // عدد الإصدارات المخزنة في قاعدة البيانات
                    Surface(
                        color = theme.cardSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, theme.separator)
                    ) {
                        Text(
                            "${changelogsState.size} إصدارات",
                            color = theme.textPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // منصة التصفية والفرز التفاعلية بنمط HUD
            item {
                ChangelogFilterDock(
                    searchQuery = changelogSearchQuery,
                    onSearchQueryChange = { changelogSearchQuery = it },
                    selectedType = changelogSelectedType,
                    onTypeSelect = { changelogSelectedType = it },
                    selectedVersion = changelogSelectedVersion,
                    onVersionSelect = { changelogSelectedVersion = it },
                    availableVersions = availableVersions,
                    sortOrder = changelogSortOrder,
                    onToggleSortOrder = {
                        changelogSortOrder = if (changelogSortOrder == ChangelogSortOrder.DATE_DESC) {
                            ChangelogSortOrder.DATE_ASC
                        } else {
                            ChangelogSortOrder.DATE_DESC
                        }
                    },
                    typeCounts = typeCounts,
                    totalMatchingCount = filteredChangelogs.size,
                    onResetFilters = {
                        changelogSearchQuery = ""
                        changelogSelectedType = ChangelogUpdateType.ALL
                        changelogSelectedVersion = null
                        changelogSortOrder = ChangelogSortOrder.DATE_DESC
                    }
                )
            }

            // سرد عناصر سجل التحديثات المصفاة مع الأيقونات الديناميكية
            if (filteredChangelogs.isEmpty()) {
                item {
                    ChangelogEmptyView(
                        onReset = {
                            changelogSearchQuery = ""
                            changelogSelectedType = ChangelogUpdateType.ALL
                            changelogSelectedVersion = null
                            changelogSortOrder = ChangelogSortOrder.DATE_DESC
                        }
                    )
                }
            } else {
                items(filteredChangelogs, key = { it.id }) { item ->
                    EnhancedChangelogCardItem(item = item)
                }
            }

            // 4️⃣ قسم تشخيص النظام وفحص التحديثات المباشر
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.background),
                    border = BorderStroke(1.dp, theme.secondarySurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "معلومات النظام والعتاد",
                            color = PresetPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("المعمارية البرمجية:", color = qabasTextSecondary(), fontFamily = NotoSansFont, fontSize = 11.sp)
                            Text("Jetpack Compose M3 + Room SQLite", color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("محرك المونتاج والتصدير:", color = qabasTextSecondary(), fontFamily = NotoSansFont, fontSize = 11.sp)
                            Text("FFmpeg Hardware Accel (1080p/4K)", color = theme.textPrimary, fontFamily = NotoSansFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الذكاء الاصطناعي والإخراج:", color = qabasTextSecondary(), fontFamily = NotoSansFont, fontSize = 11.sp)
                            Text("Qabas StyleBrain + Gemini 2.5", color = PresetAccent, fontFamily = NotoSansFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("قناة تحديثات GitHub المباشرة:", color = qabasTextSecondary(), fontFamily = NotoSansFont, fontSize = 11.sp)
                            Text("Ali-awwww/qabasapp (xdelta3) ⚡", color = theme.primary, fontFamily = RobotoMonoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // زر فحص التحديثات
                        Button(
                            onClick = {
                                isCheckingUpdates = true
                                updateResultMsg = null
                                coroutineScope.launch {
                                    try {
                                        // فحص مميِّز: كان null من checkRemoteVersion يعني «لا تحديث»
                                        // و«تعذّر الوصول» معاً، فكانت الشاشة تدّعي «متطابقة ✓» دون اتصال.
                                        when (val result = VersionManager.checkRemoteVersionDetailed(context, force = true)) {
                                            is UpdateManager.UpdateCheckResult.Available -> {
                                                val info = result.info
                                                isCheckingUpdates = false
                                                val sizeDesc = if (info.deltaUsable) {
                                                    "بحجم تفاضلي ${UpdateManager.formatSize(info.deltaSize)} فقط ⚡"
                                                } else {
                                                    "بحجم ${UpdateManager.formatSize(info.apkSize)}"
                                                }
                                                updateResultMsg = "يوجد تحديث جديد متاح: v${info.versionName} $sizeDesc 🚀"
                                                UpdateManager.setPendingUpdate(info)
                                            }
                                            UpdateManager.UpdateCheckResult.UpToDate -> {
                                                isCheckingUpdates = false
                                                updateResultMsg = "نسختك الحالية v${BuildConfig.VERSION_NAME} متطابقة مع أحدث إصدار في المستودع ✓"
                                            }
                                            UpdateManager.UpdateCheckResult.Failed,
                                            UpdateManager.UpdateCheckResult.Skipped -> {
                                                isCheckingUpdates = false
                                                updateResultMsg = "تعذّر الوصول إلى خادم التحديثات، تحقق من اتصال الإنترنت وحاول لاحقاً."
                                            }
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                                        isCheckingUpdates = false
                                        updateResultMsg = "تعذر الاتصال بالمستودع، يرجى التحقق من اتصال الإنترنت."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            enabled = !isCheckingUpdates
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جاري فحص الخادم...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فحص التحديثات الآن", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (updateResultMsg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = updateResultMsg!!,
                                color = if (updateResultMsg!!.contains("✓") || updateResultMsg!!.contains("متاح")) theme.success else theme.destructive,
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 📬 نموذج الاتصال - اقتراح ميزات أو الإبلاغ عن أخطاء
            item {
                FeedbackContactForm()
            }
        }

        // 📜 حوار سياسة الخصوصية والميثاق الكامل
        if (showFullPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { showFullPrivacyDialog = false },
                containerColor = theme.cardSurface,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = theme.success, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ميثاق الخصوصية والضوابط الشرعية", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "بسم الله الرحمن الرحيم\n«إِنَّ السَّمْعَ وَالْبَصَرَ وَالْفُؤَادَ كُلُّ أُولَٰئِكَ كَانَ عَنْهُ مَسْئُولًا»",
                            color = PresetAccent,
                            fontFamily = AmiriFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(color = theme.separator)

                        PrivacySectionItem(
                            number = "١",
                            title = "النزاهة والمحتوى الشرعي النقي",
                            desc = "يلتزم تطبيق خادم المسلم بضمان خلو كافة التصاميم والتلاوات والأصوات من أي مخالفات عقدية أو فقهية، وتخلو جميع المقاطع تماماً من المعازف والآلات الموسيقية."
                        )

                        PrivacySectionItem(
                            number = "٢",
                            title = "حماية البيانات والمشاريع",
                            desc = "تُخزن جميع المشاريع والتسجيلات الخاصة بك محلياً في قاعدة بيانات التطبيق على جهازك، ولا يتم إرسال أي نص أو وسائط إلى خوادم خارجية إلا عند طلب التوليد بالذكاء الاصطناعي."
                        )

                        PrivacySectionItem(
                            number = "٣",
                            title = "عدم بيع أو مشاركة البيانات",
                            desc = "لا نبيع بياناتك أو نشاطك لأي طرف ثالث، وتطبيق خادم المسلم خالٍ تماماً من الإعلانات التجارية المزعجة. تُستخدم بيانات الاستخدام لتحسين التطبيق فقط."
                        )

                        PrivacySectionItem(
                            number = "٤",
                            title = "التحكم الكامل بالحساب والملفات",
                            desc = "يمكنك في أي لحظة حذف حسابك أو مسح كافة الملفات المؤقتة والمشاريع من خلال إعدادات التخزين بلمسة واحدة."
                        )

                        PrivacySectionItem(
                            number = "٥",
                            title = "الذكاء الاصطناعي المنضبط والمسؤول",
                            desc = "تتم صياغة كافة الاقتباسات والأحاديث وفق أمهات كتب الحديث والتفاسير المعتمدة لأهل السنة والجماعة، ولا يتم توليد أي محتوى يمس ثوابت الدين."
                        )

                        PrivacySectionItem(
                            number = "٦",
                            title = "الدعم المباشر ومجتمع خادم المسلم",
                            desc = "يحق لك دائماً الاستفسار أو تقديم المقترحات والتواصل مع فريق العمل عبر مجموعة واتساب الرسمية للتطبيق."
                        )
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(FULL_OFFICIAL_PRIVACY_POLICY))
                                Toast.makeText(context, "تم نسخ الميثاق والسياسة كاملاً للحافظة 📋", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, PresetPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ النص 📋", color = PresetPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showFullPrivacyDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("أوافق وملتزم ✦", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            )
        }

        // 🌐 حوار تخصيص رابط السياسة الخارجي (للمطور أو لـ Google Play)
        if (showEditPrivacyUrlDialog) {
            AlertDialog(
                onDismissRequest = { showEditPrivacyUrlDialog = false },
                containerColor = theme.cardSurface,
                shape = RoundedCornerShape(18.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تخصيص رابط الويب لسياسة الخصوصية", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "التطبيق يعرض السياسة محلياً بدون حاجة لأي موقع. إذا كنت بحاجة لرابط ويب خارجي لمتطلبات متجر Google Play، يمكنك إنشاء صفحة مجانية على Google Sites أو Notion أو مستند Google Doc متاح للعامة ووضع رابطه هنا:",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = customPrivacyUrlInput,
                            onValueChange = { customPrivacyUrlInput = it },
                            placeholder = { Text("https://sites.google.com/view/...", color = theme.textTertiary, fontFamily = CairoFont, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (customPrivacyUrl.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    prefs.edit().remove("custom_privacy_url").apply()
                                    customPrivacyUrl = ""
                                    customPrivacyUrlInput = ""
                                    showEditPrivacyUrlDialog = false
                                    Toast.makeText(context, "تم مسح الرابط والعودة للنظام الداخلي 100%", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("مسح الرابط المخصص والاعتماد على العرض المحلي فقط", color = theme.destructive, fontFamily = CairoFont, fontSize = 10.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = customPrivacyUrlInput.trim()
                            prefs.edit().putString("custom_privacy_url", trimmed).apply()
                            customPrivacyUrl = trimmed
                            showEditPrivacyUrlDialog = false
                            Toast.makeText(context, "تم حفظ الرابط بنجاح ✓", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditPrivacyUrlDialog = false }) {
                        Text("إلغاء", color = theme.textTertiary, fontFamily = CairoFont)
                    }
                }
            )
        }

        // 🛠️ حوار بوابة وضع المطور — بلا حقل كلمة مرور: كان الحقل زخرفياً (لا يُقرأ
        // أبداً؛ التفعيل برتبة المطور السحابية فقط عبر AdminGuard)، فحُذف.
        if (showDeveloperGateDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeveloperGateDialog = false
                },
                containerColor = theme.background,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = PresetPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("وضع المطور 🛠️", color = PresetPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("وضع المطور يُفعَّل من سحابة Supabase فقط (رتبة مطور في حسابك)، ولا توجد كلمة مرور محلية. اضغط «فحص السحابة» للتحقق من رتبتك الحالية:", color = theme.textPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val allowed = AdminGuard.isDashboardAccessAllowed(context)
                            showDeveloperGateDialog = false
                            if (allowed) {
                                Toast.makeText(context, "تم التحقق السحابي: حسابك مفعل برتبة مطور! 🚀", Toast.LENGTH_LONG).show()
                            } else {
                                val currentRole = SupabaseServices.Auth.getUserRoleFromMetadata()
                                Toast.makeText(context, "الرتبة السحابية الحالية ($currentRole): تفعيل رتبة المطور محصور بسحابة Supabase.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary)
                    ) {
                        Text("فحص السحابة", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeveloperGateDialog = false }) {
                        Text("إلغاء", color = theme.textTertiary, fontFamily = CairoFont)
                    }
                }
            )
        }
    }
}

@Composable
fun FeedbackContactForm() {
    val theme = QabasTheme.current
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.35f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = theme.primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("مركز التواصل مع المطور", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                "الاقتراحات، البلاغات، الاستفسارات، ومتابعة الردود في مكان واحد.",
                color = qabasTextSecondary(), fontFamily = NotoSansFont, fontSize = 11.sp, textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = { AppNavigator.navigateTo(AppState.REQUEST_CHAT) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PresetPrimary)
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("فتح مركز التواصل", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { QabasWebConfig.openCommunityGroup(context) }) {
                Text("أو الانضمام إلى مجتمع خادم المسلم الرسمي", color = theme.success, fontFamily = CairoFont, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun PrivacySectionItem(number: String, title: String, desc: String) {
    val theme = QabasTheme.current
    Surface(
        color = theme.cardSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, theme.secondarySurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Surface(
                color = theme.success.copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(number, color = theme.success, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}
