package com.qabas.app

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.RobotoMonoFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelHistorySheet(
    onDismiss: () -> Unit,
    onSelectReel: (reelId: String) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    val authenticatedUser by SupabaseServices.Auth.currentUser.collectAsState()
    val activeUserId = authenticatedUser?.id?.takeIf { it.isNotBlank() }
        ?: SupabaseServices.Auth.getCurrentUserId()

    val watchHistory by ReelPreferencesManager.watchHistory.collectAsState()
    val savedReels by ReelPreferencesManager.savedReels.collectAsState()
    val likedReels by ReelPreferencesManager.likedReels.collectAsState()
    val userStats by ReelPreferencesManager.userStats.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var activitySnapshot by remember { mutableStateOf<ReelActivitySnapshot?>(null) }

    suspend fun refreshActivity(initialLoad: Boolean = false) {
        if (initialLoad) isLoading = true else isRefreshing = true
        loadFailed = false

        try {
            val userId = SupabaseServices.Auth.getCurrentUserId()
            val activity = userId?.let { SupabaseServices.Profiles.getUserReelActivity(it) }
            if (activity != null && userId == SupabaseServices.Auth.getCurrentUserId()) {
                activitySnapshot = activity
                ReelPreferencesManager.replaceActivityFromCloud(context, activity)
            } else {
                loadFailed = true
            }
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    suspend fun removeActivityItem(tab: Int, item: ReelHistoryItem) {
        val userId = SupabaseServices.Auth.getCurrentUserId()
        if (userId == null) {
            Toast.makeText(context, "تعذر قراءة حالة الحساب الآن؛ لم يتم تنفيذ العملية. أعد المحاولة بعد قليل.", Toast.LENGTH_LONG).show()
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

    LaunchedEffect(activeUserId) {
        isLoading = true
        loadFailed = false
        activitySnapshot = null
        ReelPreferencesManager.init(context)
        refreshActivity(initialLoad = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.background,
        contentColor = theme.textPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = theme.separator)
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "سجل ونشاط الريلز",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = theme.textPrimary
                        )
                        Text(
                            text = "المشاهدات والمحفوظات والإعجابات",
                            fontFamily = NotoSansFont,
                            fontSize = 12.sp,
                            color = theme.textSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedTab == 0 && watchHistory.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "مسح السجل",
                                tint = theme.destructive
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = theme.textSecondary
                        )
                    }
                }
            }

            val countsReady = !isLoading && !isRefreshing && !loadFailed
            AppleSegmentedControl(
                items = listOf(
                    0 to "السجل (${if (countsReady) userStats.totalWatched else "—"})",
                    1 to "المحفوظات (${if (countsReady) userStats.totalSaved else "—"})",
                    2 to "الإعجابات (${if (countsReady) userStats.totalLikes else "—"})",
                    3 to "الإحصائيات"
                ),
                selectedItem = selectedTab,
                onSelect = { index ->
                    selectedTab = index
                    ReelPreferencesManager.triggerHaptic(context)
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val activityListTruncated = when (selectedTab) {
                0 -> activitySnapshot?.watchHistoryTruncated == true
                1 -> activitySnapshot?.savedReelsTruncated == true
                2 -> activitySnapshot?.likedReelsTruncated == true
                else -> false
            }
            if (activityListTruncated && !isLoading && !isRefreshing && !loadFailed) {
                Text(
                    text = "تُعرض أحدث 100 نتيجة؛ بقية النشاط ما زالت محفوظة في حسابك السحابي.",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp,
                    color = theme.primary
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading || isRefreshing -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = theme.primary)
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
                                tint = theme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "تعذّر تحميل نشاط حسابك",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "تحقق من الاتصال وجلسة الدخول ثم أعد المحاولة.",
                                color = theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center
                            )
                            ApplePrimaryButton(
                                text = "إعادة المحاولة",
                                onClick = { coroutineScope.launch { refreshActivity() } },
                                modifier = Modifier.widthIn(max = 220.dp)
                            )
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
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                        subtitle = "اضغط على زر الإعجاب لإضافة المقاطع إلى قائمة إعجاباتك."
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
            containerColor = theme.cardSurface,
            title = {
                Text(
                    text = "مسح سجل المشاهدات بالكامل؟",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = theme.textPrimary
                )
            },
            text = {
                Text(
                    text = "سيتم حذف سجل المشاهدات من حسابك السحابي، مع الإبقاء على الإعجابات والمحفوظات.",
                    fontFamily = NotoSansFont,
                    fontSize = 13.5.sp,
                    color = theme.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch {
                            val userId = SupabaseServices.Auth.getCurrentUserId()
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
                    colors = ButtonDefaults.buttonColors(containerColor = theme.destructive, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("مسح الآن", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء", fontFamily = CairoFont, color = theme.textSecondary)
                }
            }
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
    val theme = QabasTheme.current
    val relativeTime = remember(item.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            item.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    AppleCard(
        onClick = onPlay,
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp, 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.secondarySurface),
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
                        tint = theme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    AsyncImage(
                        model = item.userAvatar ?: "https://ui-avatars.com/api/?name=${item.userName}&background=007AFF&color=FFFFFF",
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.userName,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = theme.textSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = relativeTime,
                        fontFamily = RobotoMonoFont,
                        fontSize = 10.5.sp,
                        color = theme.textSecondary
                    )
                }
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "تشغيل",
                    tint = theme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (showDeleteAsUnsave) Icons.Default.BookmarkRemove
                    else if (showDeleteAsUnlike) Icons.Default.Favorite
                    else Icons.Default.Close,
                    contentDescription = "حذف",
                    tint = if (showDeleteAsUnlike) theme.destructive else theme.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ReelStatsView(stats: ReelUserStats) {
    val theme = QabasTheme.current
    val totalMinutes = (stats.totalWatchSeconds / 60)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppleCard(containerColor = theme.primary.copy(alpha = 0.08f)) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = theme.primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = theme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "مستمع واعي لآيات الله",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )
                    Text(
                        text = "كل دقيقة تقضيها في تدبر القرآن والأحاديث تصنع لك أثراً ونوراً",
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReelStatCard(
                modifier = Modifier.weight(1f),
                title = "المقاطع المشاهدة",
                value = "${stats.totalWatched}",
                icon = Icons.Default.Visibility,
                color = theme.primary
            )
            ReelStatCard(
                modifier = Modifier.weight(1f),
                title = "وقت الاستماع والتدبر",
                value = if (totalMinutes < 60) "$totalMinutes دقيقة" else "${totalMinutes / 60} ساعة",
                icon = Icons.Default.Timer,
                color = theme.success
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
                color = theme.destructive
            )
            ReelStatCard(
                modifier = Modifier.weight(1f),
                title = "المقاطع المحفوظة",
                value = "${stats.totalSaved}",
                icon = Icons.Default.Bookmark,
                color = theme.warning
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
                color = theme.accent
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
    val theme = QabasTheme.current
    AppleCard(modifier = modifier, cornerRadius = 14.dp) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontFamily = NotoSansFont,
                    fontSize = 11.5.sp,
                    color = theme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
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
    val theme = QabasTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = theme.secondarySurface,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = theme.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontFamily = NotoSansFont,
                fontSize = 13.sp,
                color = theme.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
        }
    }
}

