package com.qabas.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.*
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
    val avatarColor: Color = GoldPrimary,
    val userId: String = "",
    /**
     * حراك الترتيب مقابل الأسبوع السابق: موجب = تقدّم، سالب = تراجع،
     * صفر = بلا تغيير. null تعني "لا بيانات أسبوع سابق" — فلا يظهر
     * أي سهم أبداً، ولا يُخترع سهم حراك.
     */
    val movement: Int? = null
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
    val duration: String = "30 ثانية",
    val isLikedByMe: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    onUseInspirationScript: (String, String) -> Unit = { _, _ -> },
    onOpenRewards: () -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var timeFilter by remember { mutableStateOf("الأسبوع") }

    var topCreators by remember { mutableStateOf<List<CreatorStats>>(emptyList()) }
    var feedProjects by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var currentMission by remember { mutableStateOf<PointsManager.WeeklyMission?>(null) }
    var isMissionCompleted by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    val daysLeft = remember {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val left = (Calendar.FRIDAY - dayOfWeek + 7) % 7
        if (left == 0) 7 else left
    }

    val loadData: () -> Unit = {
        coroutineScope.launch {
            isLoading = true

            val currentUserName = CloudServices.Auth.currentUser.value?.displayName
                ?: prefs.getString("user_name", "")?.takeIf { it.isNotBlank() }
                ?: prefs.getString("local_user_name", "")?.takeIf { it.isNotBlank() }
                ?: "أنت (صانع الأثر)"

            // Read synced PointsManager key ("local_user_points") cleanly without fabricated score floors
            val currentUserPoints = prefs.getInt(
                "local_user_points",
                prefs.getInt("local_points", 0)
            )

            val userProjects = ProjectService(context).getLocalProjects()
            val userBadge = when {
                currentUserPoints >= 1000 -> "سفير النور"
                currentUserPoints >= 500 -> "مبدع ذهبي"
                currentUserPoints >= 200 -> "مبدع فضي"
                else -> "صانع أثر"
            }

            // لوحة الشرف تُعاد قراءتها من السحابة لكل نافذة زمنية يختارها
            // المستخدم (الأسبوع / الأسبوع الماضي / الشهر / الكل) — الفلتر
            // ليس عرضياً: كل اختيار استعلام حقيقي بنافذته.
            val fetchedCreators = CloudServices.Database.getLeaderboard(timeFilter)

            if (fetchedCreators.isNotEmpty()) {
                val hasUser = fetchedCreators.any { it.isCurrentUser }
                // المستخدم الحالي غير موجود سحابياً وله نقاط محلية فقط:
                // يُلحق صفاً واحداً موسوماً بوضوح "نقاط محلية لم تُزامن بعد"
                // ولا يُعطى ترتيباً وهمياً حين لا نقاط له إطلاقاً. إلحاقه
                // يبدّل الترتيب، فتُحجب أسهم الحراك (محسوبة على ترتيب
                // السحابة وحده) بدل عرض أسهم خاطئة.
                val list = if (!hasUser && currentUserPoints > 0) {
                    (fetchedCreators + CreatorStats(
                        name = currentUserName,
                        rank = fetchedCreators.size + 1,
                        points = currentUserPoints,
                        views = "نقاط محلية — لم تُزامن بعد",
                        badge = userBadge,
                        isCurrentUser = true,
                        avatarColor = theme.primary
                    )).sortedByDescending { it.points }
                        .map { it.copy(movement = null) }
                } else {
                    fetchedCreators
                }

                topCreators = list.mapIndexed { idx, item -> item.copy(rank = idx + 1) }
            } else {
                // فراغ صادق: لا متصدر وهمي بالمرتبة الأولى حين لا نقاط
                // لأحد. صف محلي واحد فقط إن كانت للمستخدم نقاط محلية.
                topCreators = if (currentUserPoints > 0) {
                    listOf(
                        CreatorStats(
                            name = currentUserName,
                            rank = 1,
                            points = currentUserPoints,
                            views = "نقاط محلية — لم تُزامن بعد",
                            badge = userBadge,
                            isCurrentUser = true,
                            avatarColor = theme.primary
                        )
                    )
                } else {
                    emptyList()
                }
            }

            val cloudProjects = CloudServices.Database.getFeedProjects()
            feedProjects = if (cloudProjects.isNotEmpty()) {
                cloudProjects.mapIndexed { idx, p ->
                    FeedItem(
                        id = p.id.ifBlank { "proj_$idx" },
                        title = p.title.ifBlank { "مشروع إسلامي ملهم" },
                        creatorName = "صانع محتوى",
                        scriptPreview = p.script.ifBlank { p.idea.ifBlank { "سيناريو دعوي مبارك" } },
                        category = "إنتاج دعوي",
                        likesCount = 0,
                        viewsCount = "منشور سحابياً",
                        ratio = "9:16",
                        duration = "سحابي"
                    )
                }
            } else if (userProjects.isNotEmpty()) {
                userProjects.mapIndexed { idx, p ->
                    FeedItem(
                        id = p.id.ifBlank { "local_$idx" },
                        title = p.title.ifBlank { "مشروع دعوي مبارك" },
                        creatorName = currentUserName,
                        scriptPreview = p.script.ifBlank { p.idea.ifBlank { "سيناريو دعوي تم إنتاجه عبر خادم المسلم" } },
                        category = "مشاريعي المحفوظة",
                        likesCount = 0,
                        viewsCount = "محفوظ محلياً",
                        ratio = "9:16",
                        duration = "محلي"
                    )
                }
            } else {
                emptyList()
            }

            val mission = PointsManager.getCurrentWeeklyMission()
            currentMission = mission
            isMissionCompleted = PointsManager.isWeeklyMissionCompleted(context, mission.id)

            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(timeFilter) {
        loadData()
    }

    Scaffold(
        containerColor = theme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            Column(modifier = Modifier.background(theme.background)) {
                AppleTopBar(
                    title = Translator.tr("مجتمع خادم المسلم ولوحة الشرف"),
                    onBack = onBack,
                    actions = {
                        CloudSyncIndicator(isSyncing = isRefreshing || isLoading)
                        IconButton(
                            onClick = onOpenRewards,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = "المكافآت",
                                tint = theme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                isRefreshing = true
                                loadData()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث",
                                tint = theme.textSecondary
                            )
                        }
                    }
                )

                AppleSegmentedControl(
                    items = listOf(
                        0 to Translator.tr("لوحة الشرف"),
                        1 to Translator.tr("مجتمع الإلهام"),
                        2 to Translator.tr("مهمة الأسبوع")
                    ),
                    selectedItem = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(theme.background)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    QabasLoadingState(
                        message = Translator.tr("جاري تحديث لوحة الشرف ومجتمع المبدعين..."),
                        subMessage = Translator.tr("يتم مزامنة نقاط الأثر والتفاعل مع السحابة")
                    )
                }
            } else {
                Crossfade(targetState = selectedTab, label = "LeaderboardTabTransition") { tabIndex ->
                    when (tabIndex) {
                        0 -> LeaderboardTab(
                            topCreators = topCreators,
                            timeFilter = timeFilter,
                            daysLeft = daysLeft,
                            onTimeFilterChanged = { timeFilter = it }
                        )
                        1 -> InspirationFeedTab(
                            feedProjects = feedProjects,
                            onUseScript = onUseInspirationScript
                        )
                        2 -> WeeklyMissionTab(
                            mission = currentMission,
                            isCompleted = isMissionCompleted,
                            onStartMission = onUseInspirationScript
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardTab(
    topCreators: List<CreatorStats>,
    timeFilter: String,
    daysLeft: Int,
    onTimeFilterChanged: (String) -> Unit
) {
    val theme = QabasTheme.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        item {
            AppleCard(cornerRadius = 16.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                    .background(theme.warning.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = theme.warning,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = Translator.tr("دوري خادم المسلم الأسبوعي"),
                                    color = theme.textPrimary,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = Translator.tr("أعلى 7 مبدعين ينالون ترقية Pro مجانية"),
                                    color = theme.textSecondary,
                                    fontFamily = NotoSansFont,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            color = theme.warning.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassBottom,
                                    contentDescription = null,
                                    tint = theme.warning,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "متبقي $daysLeft أيام",
                                    color = theme.warning,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    AppleSegmentedControl(
                        items = listOf(
                            "الأسبوع" to "الأسبوع",
                            "الأسبوع الماضي" to "الأسبوع الماضي",
                            "الشهر" to "الشهر",
                            "الكل" to "الكل"
                        ),
                        selectedItem = timeFilter,
                        onSelect = onTimeFilterChanged
                    )
                }
            }
        }

        if (topCreators.size >= 3) {
            item {
                PodiumSection(
                    first = topCreators[0],
                    second = topCreators[1],
                    third = topCreators[2]
                )
            }
        } else if (topCreators.isNotEmpty()) {
            item {
                SingleLeaderHeroCard(creator = topCreators[0])
            }
        }

        if (topCreators.isEmpty()) {
            item {
                AppleCard(cornerRadius = 16.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = theme.textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = Translator.tr("لا توجد نقاط مجتمع بعد"),
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = Translator.tr("تُكتسب النقاط من تصدير فيديو أو إكمال اختبار أو الدخول اليومي، وتظهر هنا بعد مزامنتها مع السحابة."),
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
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
                    text = Translator.tr("قائمة المتصدرين وسفراء النور"),
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${topCreators.size} مبدع ($timeFilter)",
                    color = theme.primary,
                    fontFamily = NotoSansFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        itemsIndexed(topCreators) { _, creator ->
            val rankColor = when (creator.rank) {
                1 -> theme.warning
                2 -> theme.textSecondary
                3 -> theme.accent
                else -> theme.primary
            }

            AppleCard(
                containerColor = if (creator.isCurrentUser) {
                    theme.primary.copy(alpha = if (theme.isDark) 0.14f else 0.08f)
                } else {
                    theme.cardSurface
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (creator.rank <= 3) rankColor.copy(alpha = 0.18f)
                                else theme.secondarySurface
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${creator.rank}",
                            color = if (creator.rank <= 3) rankColor else theme.textPrimary,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(creator.avatarColor.copy(alpha = 0.14f))
                            .border(1.dp, creator.avatarColor.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = creator.name.take(1),
                            color = creator.avatarColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = creator.name,
                                color = if (creator.isCurrentUser) theme.primary else theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (creator.isCurrentUser) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = theme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = Translator.tr("أنت"),
                                        color = theme.primary,
                                        fontSize = 10.sp,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = rankColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = creator.badge,
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${creator.points} ${Translator.tr("نقطة")}",
                            color = theme.primary,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // سهم الحراك فقط عند وجود ترتيب أسبوع سابق حقيقي؛
                        // movement == null تعني "لا بيانات سابقة" فلا سهم.
                        when {
                            creator.movement != null && creator.movement > 0 -> Text(
                                text = "▲ ${creator.movement}",
                                color = theme.success,
                                fontFamily = NotoSansFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            creator.movement != null && creator.movement < 0 -> Text(
                                text = "▼ ${-creator.movement}",
                                color = theme.destructive,
                                fontFamily = NotoSansFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            creator.movement != null -> Text(
                                text = "– بلا تغيير",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                            creator.views.isNotBlank() -> Text(
                                text = creator.views,
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleLeaderHeroCard(creator: CreatorStats) {
    val theme = QabasTheme.current
    AppleCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(theme.primary.copy(alpha = 0.14f))
                    .border(1.5.dp, theme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = creator.name.take(1),
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = theme.warning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "فارس الصدارة الحالي: ${creator.name}",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "رصيدك الحالي: ${creator.points} نقطة أثر • الوسام: ${creator.badge}",
                color = theme.primary,
                fontFamily = CairoFont,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "استمر في إنتاج المقاطع وخوض مهام الأسبوع لكسب المزيد من النقاط.",
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PodiumSection(
    first: CreatorStats,
    second: CreatorStats,
    third: CreatorStats
) {
    val theme = QabasTheme.current
    AppleCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = theme.warning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Translator.tr("فرسان الصدارة لهذا الأسبوع"),
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                PodiumColumn(
                    creator = second,
                    rankNumber = 2,
                    height = 86.dp,
                    color = theme.textSecondary
                )
                PodiumColumn(
                    creator = first,
                    rankNumber = 1,
                    height = 116.dp,
                    color = theme.warning
                )
                PodiumColumn(
                    creator = third,
                    rankNumber = 3,
                    height = 68.dp,
                    color = theme.accent
                )
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    creator: CreatorStats,
    rankNumber: Int,
    height: Dp,
    color: Color
) {
    val theme = QabasTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            if (rankNumber == 1) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = theme.warning,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f))
                .border(1.5.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = creator.name.take(1),
                color = color,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = creator.name.split(" ").firstOrNull() ?: creator.name,
            color = theme.textPrimary,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${creator.points} ن",
            color = theme.primary,
            fontFamily = RobotoMonoFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(color.copy(alpha = if (theme.isDark) 0.25f else 0.18f))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#$rankNumber",
                color = color,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun InspirationFeedTab(
    feedProjects: List<FeedItem>,
    onUseScript: (String, String) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val prefs = remember(context) { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    var likedSet by remember {
        mutableStateOf(prefs.getStringSet("liked_feed_ids", emptySet()) ?: emptySet())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        item {
            AppleCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = Translator.tr("استلهم وأنتج مقطعك فوراً"),
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = Translator.tr("تصفح سيناريوهات المشاريع المنجزة، واقتبس الفكرة بضغطة زر."),
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (feedProjects.isEmpty()) {
            item {
                AppleCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieCreation,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "لا توجد مشاريع منشورة في المجتمع حتى الآن",
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "أنشئ مشروعك الدعوي الأول واحفظه ليظهر في مجتمع خادم المسلم وتكسب نقاط الأثر.",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(feedProjects, key = { it.id }) { item ->
                val isLiked = item.id in likedSet
                AppleCard {
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(theme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.creatorName.take(1),
                                        color = theme.primary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = item.creatorName,
                                        color = theme.textPrimary,
                                        fontFamily = CairoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = item.category,
                                        color = theme.textSecondary,
                                        fontFamily = NotoSansFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Surface(
                                color = theme.secondarySurface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = item.viewsCount,
                                    color = theme.textSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = NotoSansFont,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = item.title,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            color = theme.secondarySurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.scriptPreview,
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val updated = if (isLiked) likedSet - item.id else likedSet + item.id
                                    likedSet = updated
                                    prefs.edit().putStringSet("liked_feed_ids", updated).apply()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isLiked) "إلغاء الإعجاب" else "إعجاب",
                                    tint = if (isLiked) theme.destructive else theme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AppleSecondaryButton(
                                text = Translator.tr("استخدام الفكرة"),
                                icon = Icons.Default.PlayArrow,
                                onClick = { onUseScript(item.scriptPreview, item.title) },
                                modifier = Modifier.fillMaxWidth(0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyMissionTab(
    mission: PointsManager.WeeklyMission?,
    isCompleted: Boolean,
    onStartMission: (String, String) -> Unit
) {
    val theme = QabasTheme.current

    if (mission == null) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item { VerificationMissionCard() }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        item { VerificationMissionCard() }

        item {
            AppleCard(cornerRadius = 18.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = theme.success.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = theme.success,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "مهمة الأسبوع ${mission.weekNumber}",
                                    color = theme.success,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            color = theme.warning.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "+${mission.points} " + Translator.tr("نقطة أثر"),
                                color = theme.warning,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = mission.topic,
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = mission.description,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = theme.separator.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = Translator.tr("الموجه الذكي المخصص للتحدي:"),
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = mission.prompt,
                            color = theme.textPrimary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isCompleted) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.success.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, theme.success)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = theme.success,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.tr("تم إنجاز هذا التحدي وحصد النقاط بنجاح"),
                                    color = theme.success,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        ApplePrimaryButton(
                            text = Translator.tr("خوض التحدي وإنتاج الفيديو الآن"),
                            icon = Icons.Default.RocketLaunch,
                            onClick = { onStartMission(mission.prompt, mission.topic) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = Translator.tr("طرق كسب نقاط الأثر اليومية"),
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(PointAction.entries) { action ->
            AppleCard {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
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
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (action) {
                                    PointAction.EXPORT_VIDEO -> Icons.Default.Movie
                                    PointAction.USE_AI_HOOK -> Icons.Default.Lightbulb
                                    PointAction.SEARCH_DALEEL -> Icons.Default.Search
                                    PointAction.SHARE_APP -> Icons.Default.Share
                                    PointAction.DAILY_LOGIN -> Icons.Default.Today
                                    PointAction.WEEKLY_CHALLENGE -> Icons.Default.EmojiEvents
                                    PointAction.SAHIH_QUIZ_CORRECT -> Icons.Default.School
                                    PointAction.SAHIH_QUIZ_PERFECT -> Icons.Default.EmojiEvents
                                },
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = action.description,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = theme.success.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "+${action.points}",
                            color = theme.success,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationMissionCard() {
    val context = LocalContext.current
    val theme = QabasTheme.current
    var refresh by remember { mutableIntStateOf(0) }
    val tasks = VerificationManager.TASKS
    val completed = remember(refresh) { VerificationManager.completedTasks(context) }
    val isDev = remember(refresh) { VerificationManager.isDeveloper(context) }
    val selfVerified = remember(refresh) { VerificationManager.isSelfVerified(context) }
    var targetInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching { VerificationManager.refreshFromCloud(context) }
        refresh++
    }

    AppleCard(cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "شارة التوثيق ومهام الريلز",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "أكمل المهام المطلوبة ثم تُمنح الشارة بعد المراجعة",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = if (selfVerified) theme.success.copy(alpha = 0.14f) else theme.secondarySurface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (selfVerified) "موثّق ✓" else "$completed/${tasks.size}",
                        color = if (selfVerified) theme.success else theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "قواعد التوثيق",
                color = theme.primary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            VerificationManager.RULES.forEach { rule ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text("•", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = rule,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "المهام المطلوبة",
                color = theme.primary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            tasks.forEach { task ->
                val cur = remember(refresh, task.id) { VerificationManager.progressFor(context, task) }
                val done = cur >= task.target
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (done) theme.success else theme.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = task.description,
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 11.5.sp
                            )
                        }
                        Text(
                            text = "${cur.coerceAtMost(task.target)}/${task.target}",
                            color = if (done) theme.success else theme.primary,
                            fontFamily = RobotoMonoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { (cur.toFloat() / task.target).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (done) theme.success else theme.primary,
                        trackColor = theme.secondarySurface
                    )
                }
            }

            if (isDev) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = theme.separator.copy(alpha = 0.5f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "لوحة المطوّر — منح/سحب التوثيق",
                    color = theme.primary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it },
                    placeholder = {
                        Text(
                            text = "معرّف المستخدم أو بريده",
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.separator,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (VerificationManager.setVerified(context, targetInput, true)) {
                                targetInput = ""
                                refresh++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("توثيق", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            if (VerificationManager.setVerified(context, targetInput, false)) {
                                targetInput = ""
                                refresh++
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, theme.destructive),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = "سحب التوثيق",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = theme.destructive
                        )
                    }
                }
            }
        }
    }
}
