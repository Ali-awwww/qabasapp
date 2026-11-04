package com.qabas.app

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import com.qabas.app.ui.theme.RobotoMonoFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelHistorySheet(
    onDismiss: () -> Unit,
    onSelectReel: (reelId: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val watchHistory by ReelPreferencesManager.watchHistory.collectAsState()
    val savedReels by ReelPreferencesManager.savedReels.collectAsState()
    val likedReels by ReelPreferencesManager.likedReels.collectAsState()
    val userStats by ReelPreferencesManager.userStats.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }

    suspend fun refreshActivity(initialLoad: Boolean = false) {
        if (initialLoad) isLoading = true else isRefreshing = true
        loadFailed = false

        val userId = SupabaseServices.Auth.awaitCurrentUserId(10_000L)
        val activity = userId?.let { SupabaseServices.Profiles.getUserReelActivity(it) }
        if (activity != null) {
            ReelPreferencesManager.replaceActivityFromCloud(context, activity)
        } else {
            loadFailed = true
        }

        isLoading = false
        isRefreshing = false
    }

    suspend fun removeActivityItem(tab: Int, item: ReelHistoryItem) {
        val userId = SupabaseServices.Auth.awaitCurrentUserId(10_000L)
        if (userId == null) {
            Toast.makeText(context, "انتهت جلسة الدخول. سجّل الدخول ثم حاول مجدداً.", Toast.LENGTH_LONG).show()
            return
        }

        val removed = when (tab) {
            0 -> SupabaseServices.Profiles.removeReelWatchHistory(item.reelId, userId)
            1 -> SupabaseServices.Profiles.removeReelBookmark(item.reelId, userId)
            else -> SupabaseServices.Profiles.unlikeReel(item.reelId, userId)
        }

        if (removed) {
            refreshActivity()
            ReelPreferencesManager.triggerHaptic(context)
        } else {
            Toast.makeText(context, "تعذّر تحديث النشاط في السحابة. حاول مرة أخرى.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        ReelPreferencesManager.init(context)
        refreshActivity(initialLoad = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B1120),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "سجل ونشاط الريلز",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "WATCH HISTORY & ACTIVITY HUB",
                            fontFamily = RobotoMonoFont,
                            fontSize = 10.sp,
                            color = GoldPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedTab == 0 && watchHistory.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "مسح السجل",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    Pair("السجل (${watchHistory.size})", Icons.Default.History),
                    Pair("المحفوظات (${savedReels.size})", Icons.Default.Bookmark),
                    Pair("الإعجابات (${likedReels.size})", Icons.Default.Favorite),
                    Pair("الإحصائيات", Icons.Default.BarChart)
                )

                tabs.forEachIndexed { index, (label, icon) ->
                    val isSelected = selectedTab == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedTab = index
                                ReelPreferencesManager.triggerHaptic(context)
                            },
                        color = if (isSelected) GoldPrimary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) DeepSlate else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.5.sp,
                                color = if (isSelected) DeepSlate else Color.White.copy(alpha = 0.75f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading || isRefreshing -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GoldPrimary)
                        }
                    }
                    loadFailed -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = "تعذّر تحميل نشاط حسابك",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "تحقق من الاتصال وجلسة الدخول ثم أعد المحاولة.",
                                color = Color.White.copy(alpha = 0.65f),
                                fontFamily = CairoFont,
                                fontSize = 12.sp
                            )
                            Button(
                                onClick = { coroutineScope.launch { refreshActivity() } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldPrimary,
                                    contentColor = DeepSlate
                                )
                            ) {
                                Text("إعادة المحاولة", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        when (selectedTab) {
                            0 -> {
                                if (watchHistory.isEmpty()) {
                                    EmptyStatePlaceholder(
                                        icon = Icons.Default.HistoryToggleOff,
                                        title = "سجل المشاهدات فارغ",
                                        subtitle = "المقاطع التي تشاهدها في قسم الريلز ستظهر هنا تلقائياً لسهولة الرجوع إليها."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(watchHistory, key = { it.reelId }) { item ->
                                            ReelHistoryCard(
                                                item = item,
                                                onPlay = {
                                                    onSelectReel(item.reelId)
                                                    onDismiss()
                                                },
                                                onDelete = {
                                                    coroutineScope.launch { removeActivityItem(0, item) }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (savedReels.isEmpty()) {
                                    EmptyStatePlaceholder(
                                        icon = Icons.Default.BookmarkBorder,
                                        title = "لا توجد مقاطع محفوظة بعد",
                                        subtitle = "اضغط على أيقونة الحفظ في أي ريل لإضافته إلى قائمة المحفوظات الخاصة بك."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(savedReels, key = { it.reelId }) { item ->
                                            ReelHistoryCard(
                                                item = item,
                                                showDeleteAsUnsave = true,
                                                onPlay = {
                                                    onSelectReel(item.reelId)
                                                    onDismiss()
                                                },
                                                onDelete = {
                                                    coroutineScope.launch { removeActivityItem(1, item) }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            2 -> {
                                if (likedReels.isEmpty()) {
                                    EmptyStatePlaceholder(
                                        icon = Icons.Default.FavoriteBorder,
                                        title = "لا توجد مقاطع معجب بها بعد",
                                        subtitle = "اضغط على القلب لإضافة المقاطع إلى قائمة إعجاباتك."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(likedReels, key = { it.reelId }) { item ->
                                            ReelHistoryCard(
                                                item = item,
                                                showDeleteAsUnlike = true,
                                                onPlay = {
                                                    onSelectReel(item.reelId)
                                                    onDismiss()
                                                },
                                                onDelete = {
                                                    coroutineScope.launch { removeActivityItem(2, item) }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            3 -> ReelStatsView(stats = userStats)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "مسح سجل المشاهدات بالكامل؟",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "سيتم حذف سجل المشاهدات من حسابك السحابي، مع الإبقاء على الإعجابات والمحفوظات.",
                    fontFamily = CairoFont,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch {
                            val userId = SupabaseServices.Auth.awaitCurrentUserId(10_000L)
                            val cleared = userId?.let {
                                SupabaseServices.Profiles.clearReelWatchHistory(it)
                            } ?: false
                            if (cleared) {
                                ReelPreferencesManager.clearWatchHistory(context)
                                refreshActivity()
                                Toast.makeText(context, "تم مسح سجل المشاهدات", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "تعذّر مسح السجل من السحابة. حاول مرة أخرى.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("مسح الآن", fontFamily = CairoFont, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("إلغاء", fontFamily = CairoFont, color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = DeepSlate
        )
    }
}

@Composable
fun ReelHistoryCard(
    item: ReelHistoryItem,
    showDeleteAsUnsave: Boolean = false,
    showDeleteAsUnlike: Boolean = false,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val relativeTime = remember(item.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            item.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Icon box
            Box(
                modifier = Modifier
                    .size(54.dp, 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    AsyncImage(
                        model = item.userAvatar ?: "https://ui-avatars.com/api/?name=${item.userName}&background=D4AF37&color=0B0F19",
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.userName,
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = GoldPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = relativeTime,
                        fontFamily = RobotoMonoFont,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Actions: Play & Delete
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "تشغيل",
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (showDeleteAsUnsave) Icons.Default.BookmarkRemove
                    else if (showDeleteAsUnlike) Icons.Default.Favorite
                    else Icons.Default.Close,
                    contentDescription = "حذف",
                    tint = if (showDeleteAsUnlike) Color(0xFFEF4444) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ReelStatsView(stats: ReelUserStats) {
    val totalMinutes = (stats.totalWatchSeconds / 60)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = GoldPrimary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = GoldPrimary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = DeepSlate,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "شارة: مستمع واعي لآيات الله 🌿",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = "كل دقيقة تقضيها في تدبر القرآن والأحاديث تصنع لك أثراً ونوراً",
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // Watched and time spent
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReelStatCard(
                modifier = Modifier.weight(1f),
                title = "المقاطع المشاهدة",
                value = "${stats.totalWatched}",
                icon = Icons.Default.Visibility,
                color = Color(0xFF38BDF8)
            )
            ReelStatCard(
                modifier = Modifier.weight(1f),
                title = "وقت الاستماع والتدبر",
                value = if (totalMinutes < 60) "${totalMinutes} دقيقة" else "${totalMinutes / 60} ساعة",
                icon = Icons.Default.Timer,
                color = Color(0xFF34D399)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReelStatCard(
                modifier = Modifier.weight(1f),
                title = "الإعجابات الممنوحة",
                value = "${stats.totalLikes}",
                icon = Icons.Default.Favorite,
                color = Color(0xFFF43F5E)
            )
            ReelStatCard(
                modifier = Modifier.weight(1f),
                title = "المقاطع المحفوظة",
                value = "${stats.totalSaved}",
                icon = Icons.Default.Bookmark,
                color = GoldPrimary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReelStatCard(
                modifier = Modifier.weight(1f),
                title = "المشاركات على هذا الجهاز",
                value = "${stats.totalShares}",
                icon = Icons.Default.Share,
                color = Color(0xFF38BDF8)
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ReelStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = color
            )
        }
    }
}

@Composable
fun EmptyStatePlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = GoldPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 17.sp
            )
        }
    }
}
