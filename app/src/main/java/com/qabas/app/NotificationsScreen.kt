package com.qabas.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * مركز الإشعارات — Spatial HUD
 * حالة حيّة عبر StateFlow + عدّاد لحظي + تصميم هندسي زجاجي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenContentGuard: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val notifications by AppNotificationService.observeNotifications(context).collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    if (selectedTab == 1) {
        NotificationSettingsScreen(onBack = { selectedTab = 0 })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CutCornerShape(9.dp))
                                .background(GoldPrimary.copy(alpha = 0.14f))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CutCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(19.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = Translator.tr("مركز الإشعارات"),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (unreadCount > 0) "$unreadCount غير مقروء • بث حي" else "كل شيء تحت السيطرة",
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                color = if (unreadCount > 0) GoldPrimary else Color.White.copy(alpha = 0.45f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة", tint = GoldPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        AppSoundManager.playClick()
                        selectedTab = 1
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "إعدادات الإشعارات", tint = GoldPrimary)
                    }
                    if (notifications.isNotEmpty()) {
                        IconButton(onClick = {
                            AppSoundManager.playClick()
                            AppNotificationService.markAllRead(context)
                        }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "تعيين الكل كمقروء", tint = Color.White.copy(alpha = 0.75f))
                        }
                        IconButton(onClick = {
                            AppSoundManager.playClick()
                            AppNotificationService.clearNotifications(context)
                        }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "مسح الكل", tint = Color(0xFFEF4444).copy(alpha = 0.8f))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        },
        containerColor = DeepSlate
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            item {
                SegmentedTabs(
                    selectedTab = selectedTab,
                    unreadCount = unreadCount,
                    onSelect = {
                        AppSoundManager.playTab()
                        selectedTab = it
                    }
                )
            }

            item {
                LivePrayerHudCard(nowMillis = nowMillis)
            }

            item {
                NotificationStatsStrip(
                    total = notifications.size,
                    unread = unreadCount,
                    alerts = notifications.count { it.isTrendAlert }
                )
            }

            if (notifications.isEmpty()) {
                item {
                    QabasEmptyState(
                        modifier = Modifier.height(420.dp),
                        icon = Icons.Default.NotificationsNone,
                        title = Translator.tr("لا توجد إشعارات حالياً"),
                        description = Translator.tr("ستظهر هنا لحظة وصول أي تنبيه للصلاة أو ورد إيماني أو تنبيه من مرصد الثغور."),
                        actionButtonText = Translator.tr("ضبط إعدادات التنبيهات ⚙️"),
                        onActionClick = { selectedTab = 1 }
                    )
                }
            } else {
                items(notifications, key = { it.id }) { notif ->
                    NotificationCard(
                        notif = notif,
                        nowMillis = nowMillis,
                        onOpenContentGuard = onOpenContentGuard,
                        onMarkRead = { AppNotificationService.markRead(context, notif.id) },
                        onDelete = { AppNotificationService.deleteNotification(context, notif.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabs(
    selectedTab: Int,
    unreadCount: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TabPill(
            modifier = Modifier.weight(1f),
            selected = selectedTab == 0,
            onClick = { onSelect(0) },
            label = if (unreadCount > 0) "الواردة • $unreadCount" else "الواردة"
        )
        TabPill(
            modifier = Modifier.weight(1f),
            selected = selectedTab == 1,
            onClick = { onSelect(1) },
            label = "الإعدادات ⚙️"
        )
    }
}

@Composable
private fun TabPill(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) GoldPrimary else Color.Transparent
    ) {
        Text(
            text = label,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            color = if (selected) DeepSlate else Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        )
    }
}

@Composable
private fun LivePrayerHudCard(nowMillis: Long) {
    val context = LocalContext.current
    val city = remember { PrayerCalculationHelper.getSavedCity(context) }
    val times = remember(nowMillis / 1_000L) {
        runCatching {
            PrayerCalculationHelper.calculatePrayerTimes(
                Calendar.getInstance(),
                city,
                city.defaultMethod
            )
        }.getOrNull()
    }
    val remaining = times?.millisUntilNextPrayer?.coerceAtLeast(0L) ?: 0L
    val progress = times?.progressToNextPrayer?.coerceIn(0f, 1f) ?: 0f

    val infinite = rememberInfiniteTransition(label = "hudPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp),
        backgroundColor = Color(0xFF101827).copy(alpha = 0.9f),
        borderColor = GoldPrimary.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CutCornerShape(9.dp))
                        .background(GoldPrimary.copy(alpha = 0.12f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.35f), CutCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mosque, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "العداد اللحظي • ${times?.nextPrayerName ?: "مواقيت الصلاة"}",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GoldPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${times?.nextPrayerTimeFormatted ?: "--:--"} • 📍 ${city.nameAr}",
                        fontFamily = NotoSansFont,
                        fontSize = 10.5.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = pulse))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "LIVE",
                        fontFamily = RobotoMonoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = CutCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "المتبقي حتى الأذان",
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatCountdown(remaining),
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(GoldPrimary, Color(0xFF10B981))))
                )
            }
        }
    }
}

@Composable
private fun NotificationStatsStrip(total: Int, unread: Int, alerts: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = "غير مقروء",
            value = unread.toString(),
            accent = GoldPrimary,
            icon = Icons.Default.MarkEmailUnread
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "الإجمالي",
            value = total.toString(),
            accent = Color(0xFF38BDF8),
            icon = Icons.Default.Inbox
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "تنبيهات",
            value = alerts.toString(),
            accent = Color(0xFFEF4444),
            icon = Icons.Default.Shield
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color,
    icon: ImageVector
) {
    Surface(
        modifier = modifier,
        shape = CutCornerShape(10.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(0.8.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    fontFamily = CairoFont,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontFamily = RobotoMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = accent
            )
        }
    }
}

@Composable
fun NotificationCard(
    notif: AppNotificationService.AppNotification,
    nowMillis: Long = System.currentTimeMillis(),
    onOpenContentGuard: () -> Unit = {},
    onMarkRead: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val accent = categoryColor(notif)
    val surfaceColor by animateColorAsState(
        targetValue = if (notif.isRead) Color(0xFF111827) else Color(0xFF141E30),
        label = "cardSurface"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onMarkRead() },
        shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
        color = surfaceColor,
        border = BorderStroke(
            width = if (notif.isRead) 0.7.dp else 1.1.dp,
            color = accent.copy(alpha = if (notif.isRead) 0.15f else 0.55f)
        )
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(IntrinsicSize.Min)
                    .fillMaxHeight()
                    .background(accent.copy(alpha = if (notif.isRead) 0.35f else 1f))
            )

            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CutCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .border(1.dp, accent.copy(alpha = 0.35f), CutCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            categoryIcon(notif),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                notif.title,
                                color = if (notif.isRead) Color.White.copy(alpha = 0.82f) else Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!notif.isRead) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(label = categoryLabel(notif), color = accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatRelative(notif.timestamp, nowMillis),
                                color = Color.White.copy(alpha = 0.42f),
                                fontFamily = NotoSansFont,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            AppSoundManager.playClick()
                            onDelete()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "حذف",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    notif.message,
                    color = Color.White.copy(alpha = if (notif.isRead) 0.72f else 0.92f),
                    fontFamily = NotoSansFont,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!notif.isRead) {
                        SmallAction(
                            modifier = Modifier.weight(1f),
                            label = "تمت القراءة",
                            icon = Icons.Default.Check,
                            color = Color(0xFF10B981),
                            onClick = {
                                AppSoundManager.playClick()
                                onMarkRead()
                            }
                        )
                    }
                    SmallAction(
                        modifier = Modifier.weight(1f),
                        label = "مشاركة",
                        icon = Icons.Default.Share,
                        color = Color.White.copy(alpha = 0.75f),
                        onClick = {
                            AppSoundManager.playClick()
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "${notif.title}\n${notif.message}\n🌿 عبر استوديو قبس"
                                )
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(shareIntent, "مشاركة الإشعار عبر")
                            )
                        }
                    )
                    if (notif.isTrendAlert) {
                        SmallAction(
                            modifier = Modifier.weight(1.2f),
                            label = "مرصد الثغور",
                            icon = Icons.Default.Shield,
                            color = Color(0xFFEF4444),
                            onClick = {
                                AppSoundManager.playClick()
                                onOpenContentGuard()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(label: String, color: Color) {
    Surface(
        shape = CutCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.7.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            fontFamily = CairoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SmallAction(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CutCornerShape(7.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.09f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontFamily = CairoFont,
                fontSize = 10.5.sp,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun categoryColor(notif: AppNotificationService.AppNotification): Color = when {
    notif.isTrendAlert -> Color(0xFFEF4444)
    notif.category == AppNotificationService.CATEGORY_PRAYER -> Color(0xFF10B981)
    notif.category == AppNotificationService.CATEGORY_HADITH -> Color(0xFF38BDF8)
    notif.category == AppNotificationService.CATEGORY_SPIRITUAL -> Color(0xFFA78BFA)
    else -> GoldPrimary
}

private fun categoryIcon(notif: AppNotificationService.AppNotification): ImageVector = when {
    notif.isTrendAlert -> Icons.Default.Shield
    notif.category == AppNotificationService.CATEGORY_PRAYER -> Icons.Default.Mosque
    notif.category == AppNotificationService.CATEGORY_HADITH -> Icons.Default.AutoStories
    notif.category == AppNotificationService.CATEGORY_SPIRITUAL -> Icons.Default.NightsStay
    else -> Icons.Default.NotificationsActive
}

private fun categoryLabel(notif: AppNotificationService.AppNotification): String = when {
    notif.isTrendAlert -> "مرصد الثغور"
    notif.category == AppNotificationService.CATEGORY_PRAYER -> "مواقيت الصلاة"
    notif.category == AppNotificationService.CATEGORY_HADITH -> "حديث اليوم"
    notif.category == AppNotificationService.CATEGORY_SPIRITUAL -> "نفحة إيمانية"
    else -> "تنبيه"
}

private fun formatCountdown(millis: Long): String {
    val totalSec = (millis / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

private fun formatRelative(timestamp: Long, now: Long): String {
    val diff = (now - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        diff < 60_000L -> "الآن"
        minutes < 60L -> "منذ $minutes د"
        hours < 24L -> "منذ $hours س"
        days < 7L -> "منذ $days ي"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
