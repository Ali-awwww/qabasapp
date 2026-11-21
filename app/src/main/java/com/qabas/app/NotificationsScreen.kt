package com.qabas.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleSegmentedControl
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * مركز الإشعارات — Apple HIG Light & Dark
 * حالة حيّة عبر StateFlow + عدّاد لحظي للصلاة + إدارة كاملة للإشعارات.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenContentGuard: () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
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
        BackHandler { selectedTab = 0 }
        NotificationSettingsScreen(onBack = { selectedTab = 0 })
        return
    }

    Scaffold(
        topBar = {
            AppleTopBar(
                title = Translator.tr("مركز الإشعارات"),
                subtitle = if (unreadCount > 0) "$unreadCount غير مقروء" else "كل شيء محدث ومرتب",
                onBack = onBack,
                backTestTag = "notifications_back_btn",
                actions = {
                    if (notifications.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                AppSoundManager.playClick()
                                AppNotificationService.markAllRead(context)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("notifications_mark_all_btn")
                        ) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "تعيين الكل كمقروء",
                                tint = theme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                AppSoundManager.playClick()
                                AppNotificationService.clearNotifications(context)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("notifications_clear_all_btn")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "مسح الكل",
                                tint = theme.destructive
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            AppSoundManager.playClick()
                            selectedTab = 1
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("notifications_settings_btn")
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "إعدادات الإشعارات",
                            tint = theme.primary
                        )
                    }
                }
            )
        },
        containerColor = theme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            item {
                AppleSegmentedControl(
                    items = listOf(
                        0 to (if (unreadCount > 0) "الواردة ($unreadCount)" else "الواردة"),
                        1 to "الإعدادات"
                    ),
                    selectedItem = selectedTab,
                    onSelect = {
                        AppSoundManager.playTab()
                        selectedTab = it
                    }
                )
            }

            item {
                LivePrayerSummaryCard(nowMillis = nowMillis)
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
                        modifier = Modifier.height(380.dp),
                        icon = Icons.Default.NotificationsNone,
                        title = Translator.tr("لا توجد إشعارات حالياً"),
                        description = Translator.tr("ستظهر هنا تنبيهات مواقيت الصلاة والأوراد الإيمانية وتنبيهات مرصد الثغور."),
                        actionButtonText = Translator.tr("ضبط إعدادات التنبيهات"),
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
private fun LivePrayerSummaryCard(nowMillis: Long) {
    val context = LocalContext.current
    val theme = QabasTheme.current
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

    AppleCard(cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Mosque,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = times?.nextPrayerName ?: "مواقيت الصلاة",
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${times?.nextPrayerTimeFormatted ?: "--:--"} • ${city.nameAr}",
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = theme.success.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(theme.success)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "مباشر",
                            fontFamily = CairoFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.success
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = theme.secondarySurface.copy(alpha = if (theme.isDark) 0.7f else 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "المتبقي حتى الأذان",
                        fontFamily = CairoFont,
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                    Text(
                        text = formatCountdown(remaining),
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = theme.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0.01f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = theme.primary,
                trackColor = theme.secondarySurface
            )
        }
    }
}

@Composable
private fun NotificationStatsStrip(total: Int, unread: Int, alerts: Int) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = "غير مقروء",
            value = unread.toString(),
            accent = theme.primary,
            icon = Icons.Default.MarkEmailUnread
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "الإجمالي",
            value = total.toString(),
            accent = theme.accent,
            icon = Icons.Default.Inbox
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "تنبيهات",
            value = alerts.toString(),
            accent = theme.destructive,
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
    val theme = QabasTheme.current
    AppleCard(modifier = modifier, cornerRadius = 14.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontFamily = CairoFont,
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
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
    val theme = QabasTheme.current
    val accent = categoryColor(notif, theme)
    val cardColor by animateColorAsState(
        targetValue = if (notif.isRead) {
            theme.cardSurface
        } else {
            if (theme.isDark) theme.secondarySurface else theme.cardSurface
        },
        label = "notifCardColor"
    )

    AppleCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        cornerRadius = 16.dp,
        containerColor = cardColor,
        onClick = onMarkRead
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        categoryIcon(notif),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = notif.title,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!notif.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
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
                            color = theme.textSecondary,
                            fontFamily = NotoSansFont,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = {
                        AppSoundManager.playClick()
                        onDelete()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "حذف الإشعار",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = notif.message,
                color = if (notif.isRead) theme.textSecondary else theme.textPrimary,
                fontFamily = NotoSansFont,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                        color = theme.success,
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
                    color = theme.primary,
                    onClick = {
                        AppSoundManager.playClick()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${notif.title}\n${notif.message}\nعبر استوديو مساعد المسلم"
                            )
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "مشاركة الإشعار عبر")
                        )
                    }
                )
                if (notif.isTrendAlert) {
                    SmallAction(
                        modifier = Modifier.weight(1.2f),
                        label = "مرصد الثغور",
                        icon = Icons.Default.Shield,
                        color = theme.destructive,
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

@Composable
private fun CategoryBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            fontFamily = CairoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = CairoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun categoryColor(notif: AppNotificationService.AppNotification, theme: QabasDesignTheme): Color = when {
    notif.isTrendAlert -> theme.destructive
    notif.category == AppNotificationService.CATEGORY_PRAYER -> theme.success
    notif.category == AppNotificationService.CATEGORY_HADITH -> theme.accent
    notif.category == AppNotificationService.CATEGORY_SPIRITUAL -> GoldSecondary
    else -> theme.primary
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
