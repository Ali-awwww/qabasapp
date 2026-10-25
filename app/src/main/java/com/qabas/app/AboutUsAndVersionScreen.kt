package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.vector.ImageVector
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
ميثاق الخصوصية والضوابط الشرعية لتطبيق قبس (Qabas Studio)
الإصدار المعتمد: v1.0.0

بسم الله الرحمن الرحيم
«إِنَّ السَّمْعَ وَالْبَصَرَ وَالْفُؤَادَ كُلُّ أُولَٰئِكَ كَانَ عَنْهُ مَسْئُولًا» [الإسراء: 36]

١. النزاهة والمحتوى الشرعي النقي:
يلتزم تطبيق قبس بضمان خلو كافة التصاميم والتلاوات والأصوات والنصوص من أي مخالفات شرعية أو عقدية، وتخلو المقاطع المنتجة تماماً من المعازف والآلات الموسيقية المحرمة.

٢. التخزين المحلي المشفر وحفظ الأمانات (Offline-First):
تُخزن جميع المشاريع والتسجيلات الخاصة بك محلياً داخل جهازك في قاعدة بيانات Room مشفرة. لا نقوم برفع أو قراءة ملفاتك الشخصية، والتطبيق يعمل بشكل كامل دون الحاجة لاتصال بالإنترنت أو موقع ويب خارجي.

٣. عدم بيع أو مشاركة البيانات:
لا نقوم بجمع أو بيع بياناتك أو نشاطك لأي طرف ثالث، وتطبيق قبس خالٍ تماماً من الإعلانات التجارية وأدوات التتبع الربحية.

٤. أمان المعالجة والذكاء الاصطناعي المسؤول:
تتم معالجة نصوص الآيات والأحاديث النبوية والتفاسير وفق منهج أهل السنة والجماعة المعتمد، ولا يتم توليد أي محتوى يمس ثوابت العقيدة الإسلامية.

٥. التحكم الكامل وحذف الحساب والملفات:
يملك المستخدم السيطرة الكاملة على بياناته، مع إمكانية مسح الذاكرة المؤقتة، تصفير المشاريع، أو حذف كافة الملفات بلمسة واحدة من داخل التطبيق.

٦. التواصل والدعم الفني المباشر:
للتواصل المباشر مع فريق التطوير أو الانضمام لمجتمع قبس الرسمي على واتساب:
https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&mlu=4&ilr=4
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
    var devPassInput by remember { mutableStateOf("") }
    var devPassError by remember { mutableStateOf(false) }
    var isDevMode by remember { mutableStateOf(prefs.getBoolean("is_developer", false)) }
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

    val officialWhatsAppGroupUrl = "https://chat.whatsapp.com/FwpPfcgETYX2qw2XZE9QAF?s=cl&p=a&ilr=4&iam=2"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GoldPrimary.copy(alpha = 0.15f))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "عن قبس وسجل التحديثات",
                                color = GoldPrimary,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = GoldPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareText = "✨ تطبيق «قبس | Qabas Studio» v${BuildConfig.VERSION_NAME}\nاستوديو صناعة المحتوى الإسلامي والقرآن الكريم بالذكاء الاصطناعي.\n💬 انضم لمجتمع قبس الرسمي على واتساب:\n$officialWhatsAppGroupUrl"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة معلومات التطبيق"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = GoldPrimary)
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
                                    listOf(Color(0xFF172036), Color(0xFF0F1728), Color(0xFF090E1A))
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
                                    .background(GoldPrimary.copy(alpha = 0.15f))
                                    .border(2.dp, Brush.radialGradient(listOf(GoldPrimary, GoldSecondary, Color.Transparent)), CircleShape)
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
                                    contentDescription = "شعار قبس",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "مَنظُومَة قَبَس الإِسْلَامِيَّة",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            Text(
                                text = "Qabas Studio & Islamic Knowledge Hub",
                                color = qabasTextSecondary(),
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // شارة الإصدار الرسمية
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "الإصدار v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                        color = GoldPrimary,
                                        fontFamily = NotoSansFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // الآية الكريمة
                            Surface(
                                color = Color(0xFF090E1A).copy(alpha = 0.9f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "﴿ إِذْ رَأَىٰ نَارًا فَقَالَ لِأَهْلِهِ امْكُثُوا إِنِّي آنَسْتُ نَارًا لَّعَلِّي آتِيكُم مِّنْهَا بِقَبَسٍ ﴾",
                                        color = Color(0xFFF8FAFC),
                                        fontFamily = AmiriFont,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "سورة طه • آية 10",
                                        color = GoldSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "قبس هو استوديو متكامل في جيبك لصناعة المحتوى الدعوي والإسلامي الهادف بالذكاء الاصطناعي، يجمع بين تلاوة وتدبر القرآن الكريم، الصحيحين، الإخراج السينمائي، والتعليق الصوتي الصافي.",
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
                                    .background(GoldPrimary.copy(alpha = 0.15f))
                                    .border(1.2.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "سجل التحديثات التفصيلي",
                                    color = GoldPrimary,
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
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = GoldPrimary.copy(alpha = 0.7f))
                    }
                }
            }

            // 🌐 بطاقة الموقع الرسمي والمستودع السحابي لمنظومة قبس (Official Web Hub & Cloud Repository)
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
                                        .background(GoldPrimary.copy(alpha = 0.15f))
                                        .border(1.2.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "بوابة قبس والمستودع السحابي",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        "الموقع الرسمي لتنزيل التحديثات ومتابعة الدروس",
                                        color = qabasTextSecondary(),
                                        fontFamily = NotoSansFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "متصل سحابياً 🌐",
                                    color = Color(0xFF10B981),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = Color(0xFF09121E).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = QabasWebConfig.OFFICIAL_WEBSITE_URL,
                                    color = Color(0xFF94A3B8),
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
                                onClick = { QabasWebConfig.openWebsite(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.3f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("زيارة الموقع 🚀", color = DeepSlate, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    QabasLinkOpener.openUrl(context, QabasWebConfig.URL_LECTURES, "محاضرات قبس")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("الدروس والدرر", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(QabasWebConfig.OFFICIAL_WEBSITE_URL))
                                Toast.makeText(context, "تم نسخ رابط الموقع الرسمي للحافظة 📋", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ رابط الموقع", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 💬 بطاقة مجموعة ومجتمع قبس الرسمي (Official Community & Discussion Group)
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
                                        .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                        .border(1.2.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "مجموعة ومجتمع قبس الرسمي",
                                        color = Color(0xFF10B981),
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
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "مجتمع تفاعلي 💬",
                                    color = Color(0xFF10B981),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = Color(0xFF09121E).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = groupUrl,
                                    color = Color(0xFF94A3B8),
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
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
                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(0.9f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ الرابط", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Gavel, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "الضوابط الشرعية وسياسة الخصوصية",
                                        color = Color.White,
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
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "مدمجة أوفلاين 🛡️",
                                    color = Color(0xFF10B981),
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
                            color = Color(0xFF09121E).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2D4A))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "سياسة الخصوصية مدمجة بالكامل داخل التطبيق وتعمل دون الحاجة لأي موقع ويب أو إنترنت. كافة بياناتك ومشاريعك محفوظة بأمان محلياً على جهازك فقط.",
                                    color = Color(0xFFCBD5E1),
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
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
                                    context.startActivity(Intent.createChooser(intent, "مشاركة ميثاق وسياسة خصوصية قبس"))
                                },
                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة النص", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // زر نسخ النص كاملاً
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(FULL_OFFICIAL_PRIVACY_POLICY))
                                    Toast.makeText(context, "تم نسخ ميثاق وسياسة الخصوصية كاملاً للحافظة 📋", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ السياسة", tint = GoldPrimary, modifier = Modifier.size(18.dp))
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
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("فتح 🌐", color = GoldPrimary, fontSize = 11.sp, fontFamily = CairoFont)
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
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = GoldSecondary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (customPrivacyUrl.isBlank()) "إضافة رابط ويب لمتجر Google Play ⚙️" else "تعديل رابط الويب ⚙️",
                                        color = GoldSecondary,
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
                                .background(GoldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "سجل التحديثات والإصدارات (من قاعدة البيانات)",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // عدد الإصدارات المخزنة في قاعدة البيانات
                    Surface(
                        color = Color(0xFF151C2C),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF222B3D))
                    ) {
                        Text(
                            "${changelogsState.size} إصدارات",
                            color = Color.White,
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1424)),
                    border = BorderStroke(1.dp, Color(0xFF1E2B45))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "معلومات النظام والعتاد",
                            color = GoldPrimary,
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
                            Text("Jetpack Compose M3 + Room SQLite", color = Color.White, fontFamily = NotoSansFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("محرك المونتاج والتصدير:", color = qabasTextSecondary(), fontFamily = NotoSansFont, fontSize = 11.sp)
                            Text("FFmpeg Hardware Accel (1080p/4K)", color = Color.White, fontFamily = NotoSansFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الذكاء الاصطناعي والإخراج:", color = qabasTextSecondary(), fontFamily = NotoSansFont, fontSize = 11.sp)
                            Text("Qabas StyleBrain + Gemini 2.5", color = GoldSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("قناة تحديثات GitHub المباشرة:", color = qabasTextSecondary(), fontFamily = NotoSansFont, fontSize = 11.sp)
                            Text("Ali-awwww/qabasapp (xdelta3) ⚡", color = Color(0xFF00E5FF), fontFamily = RobotoMonoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // زر فحص التحديثات
                        Button(
                            onClick = {
                                isCheckingUpdates = true
                                updateResultMsg = null
                                coroutineScope.launch {
                                    try {
                                        val info = VersionManager.checkRemoteVersion(context, force = true)
                                        isCheckingUpdates = false
                                        if (info != null) {
                                            val sizeDesc = if (info.deltaUsable) {
                                                "بحجم تفاضلي ${UpdateManager.formatSize(info.deltaSize)} فقط ⚡"
                                            } else {
                                                "بحجم ${UpdateManager.formatSize(info.apkSize)}"
                                            }
                                            updateResultMsg = "يوجد تحديث جديد متاح: v${info.versionName} $sizeDesc 🚀"
                                            UpdateManager.setPendingUpdate(info)
                                        } else {
                                            updateResultMsg = "نسختك الحالية v${BuildConfig.VERSION_NAME} متطابقة مع أحدث إصدار في المستودع ✓"
                                        }
                                    } catch (e: Exception) {
                                        isCheckingUpdates = false
                                        updateResultMsg = "تعذر الاتصال بالمستودع، يرجى التحقق من اتصال الإنترنت."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
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
                                color = if (updateResultMsg!!.contains("✓") || updateResultMsg!!.contains("متاح")) Color(0xFF10B981) else Color(0xFFEF4444),
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
                containerColor = Color(0xFF141C2B),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ميثاق الخصوصية والضوابط الشرعية", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                            color = GoldSecondary,
                            fontFamily = AmiriFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(color = Color(0xFF222B3D))

                        PrivacySectionItem(
                            number = "١",
                            title = "النزاهة والمحتوى الشرعي النقي",
                            desc = "يلتزم تطبيق قبس بضمان خلو كافة التصاميم والتلاوات والأصوات من أي مخالفات عقدية أو فقهية، وتخلو جميع المقاطع تماماً من المعازف والآلات الموسيقية."
                        )

                        PrivacySectionItem(
                            number = "٢",
                            title = "تشفير وحماية البيانات والمشاريع",
                            desc = "تُخزن جميع المشاريع والتسجيلات الخاصة بك محلياً في قاعدة بيانات Room المشفرة، ولا يتم إرسال أي نص أو وسائط إلى خوادم خارجية إلا عند طلب التوليد بالذكاء الاصطناعي."
                        )

                        PrivacySectionItem(
                            number = "٣",
                            title = "عدم بيع أو مشاركة البيانات",
                            desc = "لا نقوم بجمع أو بيع بياناتك أو نشاطك لأي طرف ثالث، وتطبيق قبس خالٍ تماماً من الإعلانات التجارية المزعجة."
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
                            title = "الدعم المباشر ومجتمع قبس",
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
                            border = BorderStroke(1.dp, GoldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ النص 📋", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showFullPrivacyDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
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
                containerColor = Color(0xFF141C2B),
                shape = RoundedCornerShape(18.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تخصيص رابط الويب لسياسة الخصوصية", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "التطبيق يعرض السياسة محلياً بدون حاجة لأي موقع. إذا كنت بحاجة لرابط ويب خارجي لمتطلبات متجر Google Play، يمكنك إنشاء صفحة مجانية على Google Sites أو Notion أو مستند Google Doc متاح للعامة ووضع رابطه هنا:",
                            color = Color(0xFFCBD5E1),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = customPrivacyUrlInput,
                            onValueChange = { customPrivacyUrlInput = it },
                            placeholder = { Text("https://sites.google.com/view/...", color = Color.Gray, fontFamily = CairoFont, fontSize = 11.sp) },
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
                                Text("مسح الرابط المخصص والاعتماد على العرض المحلي فقط", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 10.sp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditPrivacyUrlDialog = false }) {
                        Text("إلغاء", color = Color.LightGray, fontFamily = CairoFont)
                    }
                }
            )
        }

        // 🛠️ حوار بوابة وضع المطور
        if (showDeveloperGateDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeveloperGateDialog = false
                    devPassInput = ""
                    devPassError = false
                },
                containerColor = Color(0xFF0D1117),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تفعيل وضع المطور السري 🛠️", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("أدخل كلمة المرور لتفعيل أدوات المطور ولوحة التحكم المتقدمة:", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
                        OutlinedTextField(
                            value = devPassInput,
                            onValueChange = { devPassInput = it; devPassError = false },
                            placeholder = { Text("كلمة المرور", color = Color.Gray, fontFamily = CairoFont) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (devPassError) {
                            Text("كلمة المرور غير صحيحة", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val salt = "Qbs::DevGate::v1"
                            val digest = java.security.MessageDigest.getInstance("SHA-256")
                                .digest((devPassInput + salt).toByteArray())
                                .joinToString("") { "%02x".format(it) }
                            val expected = "284308ac" + "cd46b74003578263" + "92758c1e0d189e543" + "43d3777156a550cbb123bfa"
                            if (digest == expected) {
                                prefs.edit().putBoolean("is_developer", true).apply()
                                isDevMode = true
                                showDeveloperGateDialog = false
                                devPassInput = ""
                                Toast.makeText(context, "تم تفعيل وضع المطور بنجاح! 🚀", Toast.LENGTH_LONG).show()
                            } else {
                                devPassError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("تفعيل", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeveloperGateDialog = false }) {
                        Text("إلغاء", color = Color.Gray, fontFamily = CairoFont)
                    }
                }
            )
        }
    }
}

@Composable
fun FeedbackContactForm() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var feedbackType by remember { mutableStateOf("FEATURE_SUGGESTION") } // BUG_REPORT, FEATURE_SUGGESTION
    var message by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showUserReportsDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .luxuryCardStyle(shapeRadius = 18.dp, borderAlpha = 0.35f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = qabasCardSurface())
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mail, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "تواصل معنا وشاركنا رأيك",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "اقتراح ميزات جديدة أو الإبلاغ عن مشكلة تقنية",
                        color = qabasTextSecondary(),
                        fontFamily = NotoSansFont,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // اختيار نوع الرسالة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeedbackTypeChip(
                    label = "اقتراح ميزة ✨",
                    isSelected = feedbackType == "FEATURE_SUGGESTION",
                    color = Color(0xFF10B981),
                    onClick = { feedbackType = "FEATURE_SUGGESTION" },
                    modifier = Modifier.weight(1f)
                )
                FeedbackTypeChip(
                    label = "إبلاغ عن خطأ ⚠️",
                    isSelected = feedbackType == "BUG_REPORT",
                    color = Color(0xFFEF4444),
                    onClick = { feedbackType = "BUG_REPORT" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // حقل الاسم
            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("الاسم (اختياري)", fontFamily = CairoFont, fontSize = 11.sp) },
                placeholder = { Text("مثلاً: طالب علم", color = Color.Gray, fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF2A344A),
                    focusedLabelColor = GoldPrimary,
                    cursorColor = GoldPrimary
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = CairoFont, fontSize = 12.sp, color = Color.White),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // حقل الرسالة
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("اكتب رسالتك هنا...", fontFamily = CairoFont, fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF2A344A),
                    focusedLabelColor = GoldPrimary,
                    cursorColor = GoldPrimary
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = CairoFont, fontSize = 12.sp, color = Color.White)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // زر الإرسال
            Button(
                onClick = {
                    if (message.isBlank()) {
                        Toast.makeText(context, "يرجى كتابة الرسالة أولاً", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        isSubmitting = true
                        val result = SupabaseServices.Feedback.submitFeedback(
                            type = feedbackType,
                            message = message,
                            userName = userName
                        )
                        isSubmitting = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "تم إرسال رسالتك بنجاح! شكراً لمساهمتك 🌟", Toast.LENGTH_LONG).show()
                            message = ""
                        } else {
                            // Fallback to Email if Supabase fails
                            Toast.makeText(context, "فشل الإرسال سحابياً، جاري فتح بريد الدعم...", Toast.LENGTH_SHORT).show()
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:support@qabas.studio")
                                putExtra(Intent.EXTRA_SUBJECT, "Qabas App Feedback: $feedbackType")
                                putExtra(Intent.EXTRA_TEXT, "User: $userName\nMessage: $message\nDevice: ${android.os.Build.MODEL}")
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "إرسال عبر البريد"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "لا يوجد تطبيق بريد مثبت", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepSlate, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال الآن 🚀", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // زر استعراض ومتابعة الطلبات السابقة
            OutlinedButton(
                onClick = { showUserReportsDialog = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary)
            ) {
                Icon(Icons.Default.ListAlt, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("📋 متابعة سجل طلباتي وبلاغاتي", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }

    if (showUserReportsDialog) {
        UserReportsDialog(onDismissRequest = { showUserReportsDialog = false })
    }
}

@Composable
fun FeedbackTypeChip(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) color.copy(alpha = 0.2f) else Color(0xFF151D2C),
        border = BorderStroke(1.dp, if (isSelected) color else Color(0xFF2A344A)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (isSelected) color else Color.Gray,
                fontFamily = CairoFont,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun ChangelogCardItem(item: ChangelogEntity) {
    EnhancedChangelogCardItem(item = item)
}

@Composable
fun PrivacySectionItem(number: String, title: String, desc: String) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(number, color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, color = qabasTextSecondary(), fontFamily = CairoFont, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}
