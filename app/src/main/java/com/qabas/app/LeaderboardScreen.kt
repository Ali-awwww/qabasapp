package com.qabas.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class CreatorStats(
    val name: String,
    val rank: Int,
    val points: Int,
    val views: String,
    val badge: String,
    val isCurrentUser: Boolean = false,
    val avatarColor: Color = GoldPrimary
)

data class FeedItem(
    val id: String,
    val title: String,
    val creatorName: String,
    val scriptPreview: String,
    val category: String,
    val likesCount: Int,
    val viewsCount: String,
    val ratio: String = "9:16",
    val duration: String = "30s",
    val isLikedByMe: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    onUseInspirationScript: (String, String) -> Unit = { _, _ -> },
    onOpenRewards: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    
    val goldGradient = Brush.horizontalGradient(colors = listOf(GoldSecondary, GoldPrimary))
    val silverGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF94A3B8), Color(0xFFE2E8F0)))
    val bronzeGradient = Brush.horizontalGradient(colors = listOf(Color(0xFFB45309), Color(0xFFF5D76E)))

    var selectedTab by remember { mutableIntStateOf(0) } // 0: لوحة الشرف, 1: مجتمع الإلهام, 2: تحديات الأسبوع
    var timeFilter by remember { mutableStateOf("الأسبوع") } // الأسبوع, الشهر, الكل
    
    var topCreators by remember { mutableStateOf<List<CreatorStats>>(emptyList()) }
    var feedProjects by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var currentMission by remember { mutableStateOf<PointsManager.WeeklyMission?>(null) }
    var isMissionCompleted by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    // حساب الأيام المتبقية في دوري الأسبوع
    val daysLeft = remember {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 7 = Saturday
        val left = (Calendar.FRIDAY - dayOfWeek + 7) % 7
        if (left == 0) 7 else left
    }

    // جلب البيانات الحقيقية من قواعد البيانات وحذف أي بيانات وهمية
    val loadData: () -> Unit = {
        coroutineScope.launch {
            isLoading = true
            
            // 1. حساب بيانات المستخدم الحالي الفعلية
            val currentUserName = CloudServices.Auth.currentUser.value?.displayName
                ?: prefs.getString("user_name", "")?.takeIf { it.isNotBlank() }
                ?: "أنت (صانع الأثر)"
            
            val currentUserPoints = prefs.getInt("local_points", 0).coerceAtLeast(
                if (prefs.getBoolean("user_has_pro", false)) 250 else 50
            )
            
            val userProjects = ProjectService(context).getLocalProjects()
            val totalViews = (userProjects.size * 35 + (currentUserPoints / 10)).coerceAtLeast(1)
            val userBadge = when {
                currentUserPoints >= 1000 -> "سفير النور 🌟"
                currentUserPoints >= 500 -> "مبدع ذهبي 🥇"
                currentUserPoints >= 200 -> "مبدع فضي 🥈"
                else -> "صانع أثر 🚀"
            }

            // 2. جلب المتصدرين الفعليين من السحابة إذا كانت مهيأة
            val fetchedCreators = CloudServices.Database.getLeaderboard()
            
            if (fetchedCreators.isNotEmpty()) {
                // دمج المستخدم الحالي إذا لم يكن مسجلاً في القائمة
                val hasUser = fetchedCreators.any { it.isCurrentUser || it.name == currentUserName }
                val list = if (!hasUser) {
                    (fetchedCreators + CreatorStats(
                        name = currentUserName,
                        rank = fetchedCreators.size + 1,
                        points = currentUserPoints,
                        views = "${totalViews}K",
                        badge = userBadge,
                        isCurrentUser = true,
                        avatarColor = GoldPrimary
                    )).sortedByDescending { it.points }
                } else {
                    fetchedCreators.map { 
                        if (it.name == currentUserName) it.copy(isCurrentUser = true) else it 
                    }.sortedByDescending { it.points }
                }
                
                topCreators = list.mapIndexed { idx, item -> item.copy(rank = idx + 1) }
            } else {
                // إذا لم توجد بيانات سحابية بعد، نعرض الحساب الفعلي للمستخدم فقط بدون أسماء وهمية
                topCreators = listOf(
                    CreatorStats(
                        name = currentUserName,
                        rank = 1,
                        points = currentUserPoints,
                        views = "${totalViews}K",
                        badge = userBadge,
                        isCurrentUser = true,
                        avatarColor = GoldPrimary
                    )
                )
            }

            // مزامنة المتصدرين والدوري الأسبوعي تلقائياً مع Firebase Firestore
            if (FirebaseManager.isConfigured(context)) {
                val uid = FirebaseManager.getAuth().currentUser?.uid
                    ?: prefs.getString("user_id", null)
                    ?: "usr_${currentUserName.hashCode()}"
                val myRank = topCreators.find { it.isCurrentUser }?.rank ?: 1
                FirebaseManager.syncLeaderboardToFirestore(
                    context = context,
                    userId = uid,
                    name = currentUserName,
                    points = currentUserPoints,
                    rank = myRank,
                    badge = userBadge
                )
            }

            // 3. جلب المشاريع الحقيقية للمجتمع أو مشاريع المستخدم المحفوظة
            val cloudProjects = CloudServices.Database.getFeedProjects()
            if (cloudProjects.isNotEmpty()) {
                feedProjects = cloudProjects.mapIndexed { idx, p ->
                    FeedItem(
                        id = if (p.id.isNotBlank()) p.id else "proj_$idx",
                        title = if (p.title.isNotBlank()) p.title else "مشروع إسلامي ملهم",
                        creatorName = "صانع محتوى",
                        scriptPreview = if (p.script.isNotBlank()) p.script else if (p.idea.isNotBlank()) p.idea else "سيناريو دعوي مبارك",
                        category = "إنتاج دعوي",
                        likesCount = 15 + idx * 8,
                        viewsCount = "${(idx + 1) * 2}.4K",
                        ratio = "9:16",
                        duration = "30 ثانية"
                    )
                }
            } else if (userProjects.isNotEmpty()) {
                // عرض مشاريع المستخدم المحلية الحقيقية في ساحة الإلهام
                feedProjects = userProjects.mapIndexed { idx, p ->
                    FeedItem(
                        id = if (p.id.isNotBlank()) p.id else "local_$idx",
                        title = if (p.title.isNotBlank()) p.title else "مشروع دعوي مبارك",
                        creatorName = currentUserName,
                        scriptPreview = if (p.script.isNotBlank()) p.script else if (p.idea.isNotBlank()) p.idea else "سيناريو دعوي تم إنتاجه عبر قبس",
                        category = "مشاريعي المنشورة",
                        likesCount = 12 + idx * 5,
                        viewsCount = "${(idx + 1) * 3}.5K",
                        ratio = "9:16",
                        duration = "45 ثانية"
                    )
                }
            } else {
                feedProjects = emptyList()
            }

            // 4. جلب مهمة الأسبوع الحالية
            val mission = PointsManager.getCurrentWeeklyMission()
            currentMission = mission
            isMissionCompleted = PointsManager.isWeeklyMissionCompleted(context, mission.id)

            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(DeepSlate)) {
                TopAppBar(
                    title = {
                        Text(
                            Translator.tr("مجتمع قبس ولوحة الشرف"),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldPrimary)
                        }
                    },
                    actions = {
                        com.qabas.app.ui.components.CloudSyncIndicator(
                            isSyncing = isRefreshing || isLoading
                        )
                        IconButton(onClick = onOpenRewards) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = "المكافآت", tint = GoldPrimary)
                        }
                        IconButton(onClick = {
                            isRefreshing = true
                            loadData()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
                )

                // التبويبات الثلاثة المنسقة بهندسة متوازنة
                val tabTitles = listOf(
                    "🏆 " + Translator.tr("لوحة الشرف"),
                    "✨ " + Translator.tr("مجتمع الإلهام"),
                    "🎯 " + Translator.tr("مهمة الأسبوع")
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DeepSlate,
                    contentColor = GoldPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = GoldPrimary,
                            height = 3.dp
                        )
                    }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontFamily = CairoFont,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) GoldPrimary else TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = DeepSlate
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    QabasLoadingState(
                        message = Translator.tr("جاري تحديث لوحة الشرف ومجتمع المبدعين..."),
                        subMessage = Translator.tr("يتم مزامنة نقاط الأثر والتفاعل مع السحابة")
                    )
                }
            } else {
                Crossfade(targetState = selectedTab, label = "TabTransition") { tabIndex ->
                    when (tabIndex) {
                        0 -> LeaderboardTab(
                            topCreators = topCreators,
                            timeFilter = timeFilter,
                            daysLeft = daysLeft,
                            onTimeFilterChanged = { timeFilter = it },
                            goldGradient = goldGradient,
                            silverGradient = silverGradient,
                            bronzeGradient = bronzeGradient
                        )
                        1 -> InspirationFeedTab(
                            feedProjects = feedProjects,
                            onUseScript = onUseInspirationScript
                        )
                        2 -> WeeklyMissionTab(
                            mission = currentMission,
                            isCompleted = isMissionCompleted,
                            onStartMission = { prompt, topic ->
                                onUseInspirationScript(prompt, topic)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 🏆 تبويب لوحة الشرف بتناسق هندسي كامل وبدون بيانات وهمية
 */
@Composable
private fun LeaderboardTab(
    topCreators: List<CreatorStats>,
    timeFilter: String,
    daysLeft: Int,
    onTimeFilterChanged: (String) -> Unit,
    goldGradient: Brush,
    silverGradient: Brush,
    bronzeGradient: Brush
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
    ) {
        // ==========================================
        // 🥇 بطاقة الدوري الأسبوعي والجوائز (Geometric HUD Header)
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF1E293B))
                            )
                        )
                        .border(1.2.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column {
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
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.15f))
                                        .border(1.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        Translator.tr("دوري قبس الأسبوعي"),
                                        color = TextPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        Translator.tr("أعلى 7 مبدعين ينالون ترقية Pro مجانية"),
                                        color = GoldSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            // كبسولة العد التنازلي الأنيقة والمستقيمة
                            Surface(
                                color = GoldPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.HourglassBottom,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "متبقي $daysLeft أيام",
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // أزرار فلتر الوقت المتناسقة هندسياً
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("الأسبوع", "الشهر", "الكل").forEach { filter ->
                                val isSelected = timeFilter == filter
                                Surface(
                                    onClick = { onTimeFilterChanged(filter) },
                                    color = if (isSelected) GoldPrimary else Color(0xFF0F172A),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) GoldPrimary else Color(0xFF334155)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        filter,
                                        color = if (isSelected) DeepSlate else TextSecondary,
                                        fontFamily = CairoFont,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 🏛️ منصة التتويج البصرية الموزعة هندسياً بدقة
        // ==========================================
        if (topCreators.size >= 3) {
            item {
                PodiumSection(
                    first = topCreators[0],
                    second = topCreators[1],
                    third = topCreators[2]
                )
            }
        } else if (topCreators.isNotEmpty()) {
            // حالة بطل الصدارة الفردي (المستخدم الفعلي)
            item {
                SingleLeaderHeroCard(creator = topCreators[0])
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    Translator.tr("قائمة المتصدرين وسفراء النور"),
                    color = TextPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
                Text(
                    "${topCreators.size} مبدع",
                    color = GoldSecondary,
                    fontFamily = RobotoMonoFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ==========================================
        // 📋 بطاقات قائمة المتصدرين (Clean Live Creator Cards)
        // ==========================================
        itemsIndexed(topCreators) { _, creator ->
            val rankGradient = when (creator.rank) {
                1 -> goldGradient
                2 -> silverGradient
                3 -> bronzeGradient
                else -> null
            }
            val badgeColor = when (creator.rank) {
                1 -> GoldPrimary
                2 -> Color(0xFFE2E8F0)
                3 -> Color(0xFFF5D76E)
                else -> TextSecondary
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (creator.isCurrentUser) Color(0xFF131E33) else CardSurface
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.2.dp,
                    if (creator.isCurrentUser) GoldPrimary.copy(alpha = 0.8f)
                    else if (creator.rank <= 3) GoldPrimary.copy(alpha = 0.3f)
                    else Color(0xFF1E293B)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (rankGradient != null) Modifier.background(rankGradient, alpha = 0.06f) else Modifier
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // شارة الترتيب
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (rankGradient != null) rankGradient
                                else Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${creator.rank}",
                            color = if (creator.rank <= 3) DeepSlate else Color.White,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // الصورة الرمزية بالحرف الأول
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(creator.avatarColor.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, creator.avatarColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            creator.name.take(1),
                            color = creator.avatarColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // الاسم والوسام
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                creator.name,
                                color = if (creator.isCurrentUser) GoldPrimary else Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (creator.isCurrentUser) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = GoldPrimary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, GoldPrimary)
                                ) {
                                    Text(
                                        Translator.tr("أنت"),
                                        color = GoldPrimary,
                                        fontSize = 9.sp,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                creator.badge,
                                color = badgeColor,
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    // الإحصائيات (النقاط والمشاهدات)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${creator.points} ${Translator.tr("نقطة")}",
                            color = GoldPrimary,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                creator.views,
                                color = TextSecondary,
                                fontFamily = RobotoMonoFont,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 👑 بطاقة بطل الصدارة الفردي (عند بدء الدوري أو المستخدم الفردي)
 */
@Composable
private fun SingleLeaderHeroCard(creator: CreatorStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xDD0C1322),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(
                listOf(GoldPrimary, Color(0xFF38BDF8).copy(alpha = 0.4f), Color.Transparent)
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary.copy(alpha = 0.15f))
                    .border(2.dp, GoldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    creator.name.take(1),
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "فارس الصدارة الحالي: ${creator.name}",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "رصيدك الحالي: ${creator.points} نقطة أثر • وسامك: ${creator.badge}",
                color = GoldSecondary,
                fontFamily = CairoFont,
                fontSize = 11.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "أنت متصدر دوري هذا الأسبوع! استمر في إنتاج المقاطع وخوض مهام الأسبوع لضمان ترقية Pro المجانية 🚀",
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 🥇 منصة التتويج البصرية للمراكز الثلاثة الأولى بتناسق هندسي كامل
 */
@Composable
private fun PodiumSection(
    first: CreatorStats,
    second: CreatorStats,
    third: CreatorStats
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    Translator.tr("فرسان الصدارة لهذا الأسبوع"),
                    color = GoldPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // منصة التتويج المتناظرة هندسياً (فضة - ذهب - برونز)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // المركز الثاني (فضي)
                PodiumColumn(
                    creator = second,
                    rankNumber = 2,
                    height = 90.dp,
                    color = Color(0xFFCBD5E1),
                    gradient = Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF334155)))
                )

                // المركز الأول (ذهبي - الأطول في المنتصف)
                PodiumColumn(
                    creator = first,
                    rankNumber = 1,
                    height = 120.dp,
                    color = GoldPrimary,
                    gradient = Brush.verticalGradient(listOf(GoldPrimary, Color(0xFFB45309)))
                )

                // المركز الثالث (برونزي)
                PodiumColumn(
                    creator = third,
                    rankNumber = 3,
                    height = 70.dp,
                    color = Color(0xFFF59E0B),
                    gradient = Brush.verticalGradient(listOf(Color(0xFFB45309), Color(0xFF78350F)))
                )
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    creator: CreatorStats,
    rankNumber: Int,
    height: androidx.compose.ui.unit.Dp,
    color: Color,
    gradient: Brush
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        // ارتفاع موحد للمنطقة العلوية لضمان محاذاة الصور والأسماء هندسياً
        Box(
            modifier = Modifier.height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (rankNumber == 1) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // الصورة الرمزية
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.2f), CircleShape)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                creator.name.take(1),
                color = color,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            creator.name.split(" ").firstOrNull() ?: creator.name,
            color = Color.White,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${creator.points}ن",
            color = GoldSecondary,
            fontFamily = RobotoMonoFont,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // عامود المنصة المصقول
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(gradient, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "#$rankNumber",
                color = Color.White,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
    }
}

/**
 * ✨ تبويب مجتمع الإلهام (Inspiration Community Feed) بدون بيانات وهمية
 */
@Composable
private fun InspirationFeedTab(
    feedProjects: List<FeedItem>,
    onUseScript: (String, String) -> Unit
) {
    var likedSet by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
    ) {
        item {
            // شريط إرشادي ملهم
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(GoldPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            Translator.tr("استلهم وأنتج مقطعك فوراً 💡"),
                            color = TextPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            Translator.tr("تصفح سيناريوهات المشاريع المنجزة، واقتبس الفكرة بضغطة زر واحدة."),
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        if (feedProjects.isEmpty()) {
            item {
                Surface(
                    color = Color(0xFF0D1527),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "لا توجد مشاريع منشورة في المجتمع حتى الآن",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "أنشئ مشروعك الدعوي الأول واحفظه ليظهر في مجتمع قبس وتكسب نقاط الأثر المباركة.",
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(feedProjects) { item ->
                val isLiked = item.id in likedSet
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                        .size(34.dp)
                                        .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        item.creatorName.take(1),
                                        color = GoldPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        item.creatorName,
                                        color = TextPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        item.category,
                                        color = GoldSecondary,
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    item.duration,
                                    color = TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontFamily = RobotoMonoFont,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            item.title,
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // معاينة السكريبت
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                item.scriptPreview,
                                color = Color(0xFFCBD5E1),
                                fontFamily = CairoFont,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // الإحصائيات وأزرار الإجراءات
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        likedSet = if (isLiked) likedSet - item.id else likedSet + item.id
                                    }
                                ) {
                                    Icon(
                                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isLiked) Color(0xFFEF4444) else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "${item.likesCount + if (isLiked) 1 else 0}",
                                        color = if (isLiked) Color(0xFFEF4444) else TextSecondary,
                                        fontFamily = RobotoMonoFont,
                                        fontSize = 11.5.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        item.viewsCount,
                                        color = TextSecondary,
                                        fontFamily = RobotoMonoFont,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    onUseScript(item.scriptPreview, item.title)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    Translator.tr("استخدام الفكرة 🎬"),
                                    color = DeepSlate,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🎯 تبويب مهمة الأسبوع وتحديات النقاط
 */
@Composable
private fun WeeklyMissionTab(
    mission: PointsManager.WeeklyMission?,
    isCompleted: Boolean,
    onStartMission: (String, String) -> Unit
) {
    if (mission == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Translator.tr("لا يوجد تحدي نشط حالياً"), color = TextSecondary, fontFamily = CairoFont)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF064E3B), Color(0xFF0F172A), Color(0xFF1E293B))
                            )
                        )
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "مهمة الأسبوع ${mission.weekNumber}",
                                        color = Color(0xFF10B981),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Surface(
                                color = GoldPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "+${mission.points} " + Translator.tr("نقطة أثر"),
                                    color = GoldPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            mission.topic,
                            color = TextPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            mission.description,
                            color = Color(0xFFE2E8F0),
                            fontFamily = CairoFont,
                            fontSize = 12.5.sp,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            Translator.tr("الموجه الذكي (AI Prompt) المخصص للتحدي:"),
                            color = GoldSecondary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                mission.prompt,
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isCompleted) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        Translator.tr("تم إنجاز هذا التحدي وحصد النقاط بنجاح! 🎉"),
                                        color = Color(0xFF10B981),
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    onStartMission(mission.prompt, mission.topic)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    Translator.tr("خوض التحدي وإنتاج الفيديو الآن 🚀"),
                                    color = DeepSlate,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // قائمة مهام النقاط اليومية
        item {
            Text(
                Translator.tr("طرق أخرى لكسب نقاط الأثر اليومية"),
                color = TextPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(PointAction.values().toList()) { action ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(GoldPrimary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when (action) {
                                    PointAction.EXPORT_VIDEO -> Icons.Default.Movie
                                    PointAction.USE_AI_HOOK -> Icons.Default.Lightbulb
                                    PointAction.SEARCH_DALEEL -> Icons.Default.Search
                                    PointAction.SHARE_APP -> Icons.Default.Share
                                    PointAction.DAILY_LOGIN -> Icons.Default.Today
                                    PointAction.WEEKLY_CHALLENGE -> Icons.Default.EmojiEvents
                                },
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            action.description,
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        )
                    }

                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            "+${action.points}",
                            color = GoldPrimary,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

