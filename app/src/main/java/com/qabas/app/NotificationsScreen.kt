package com.qabas.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenContentGuard: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: الإشعارات الواردة, 1: إعدادات الإشعارات

    var notifications by remember { 
        val list = AppNotificationService.getNotifications(context)
        if (list.isEmpty()) {
            AppNotificationService.sendNotification(
                context,
                "🚨 تنبيه عاجل من مرصد الثغور!",
                "رصد موجة تشكيك عاجلة تستهدف ثبات الشباب والالتزام. يُرجى الدخول لمرصد الثغور للمدافعة بنشر المقاطع.",
                isTrendAlert = true
            )
            AppNotificationService.sendNotification(
                context,
                "⚡ تريند يحتاج مدافعة سريعة",
                "انتشار تريند ساخر من قيم الأسرة وبر الوالدين. تم إعداد رد قاطع ومحكم بالذكاء الاصطناعي.",
                isTrendAlert = true
            )
            mutableStateOf(AppNotificationService.getNotifications(context))
        } else {
            mutableStateOf(list)
        }
    }

    if (selectedTab == 1) {
        NotificationSettingsScreen(onBack = { selectedTab = 0 })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = Translator.tr("الإشعارات والتنبيهات 🔔"),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "العودة", tint = GoldPrimary)
                        }
                    },
                    actions = {
                        // زر الانتقال المباشر للإعدادات
                        IconButton(onClick = {
                            AppSoundManager.playClick()
                            selectedTab = 1
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "إعدادات الإشعارات", tint = GoldPrimary)
                        }

                        if (notifications.isNotEmpty()) {
                            IconButton(onClick = {
                                AppSoundManager.playClick()
                                AppNotificationService.clearNotifications(context)
                                notifications = emptyList()
                            }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "مسح الكل", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
                )
            },
            containerColor = DeepSlate
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // شريط التبديل بين الإشعارات الواردة وإعدادات الإشعارات
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                AppSoundManager.playTab()
                                selectedTab = 0
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedTab == 0) GoldPrimary else Color.Transparent
                    ) {
                        Text(
                            text = "الإشعارات الواردة (${notifications.size})",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (selectedTab == 0) DeepSlate else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                AppSoundManager.playTab()
                                selectedTab = 1
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedTab == 1) GoldPrimary else Color.Transparent
                    ) {
                        Text(
                            text = "إعدادات الإشعارات ⚙️",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (selectedTab == 1) DeepSlate else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (notifications.isEmpty()) {
                    QabasEmptyState(
                        icon = Icons.Default.Notifications,
                        title = Translator.tr("لا توجد إشعارات حالياً"),
                        description = Translator.tr("عندما تصلك إشعارات جديدة أو تنبيهات المدافعة عن الثغور وموجات التريند ستظهر هنا فوراً."),
                        actionButtonText = Translator.tr("ضبط إعدادات التنبيهات ⚙️"),
                        onActionClick = { selectedTab = 1 }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(notifications, key = { it.id }) { notif ->
                            NotificationCard(notif, onOpenContentGuard)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notif: AppNotificationService.AppNotification,
    onOpenContentGuard: () -> Unit = {}
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = if (notif.isTrendAlert) BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (notif.isTrendAlert) Color(0xFF7F1D1D) else DeepSlate,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (notif.isTrendAlert) Icons.Default.Shield else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (notif.isTrendAlert) Color(0xFFEF4444) else GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            notif.title, 
                            color = if (notif.isTrendAlert) Color(0xFFEF4444) else GoldPrimary, 
                            fontFamily = CairoFont, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 14.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val date = SimpleDateFormat("dd/MM - hh:mm a", Locale.getDefault()).format(Date(notif.timestamp))
                        Text(
                            date, 
                            color = Color.Gray, 
                            fontFamily = NotoSansFont, 
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        notif.message, 
                        color = Color.White.copy(alpha = 0.9f), 
                        fontFamily = NotoSansFont, 
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // أدوات تفاعلية للإشعار (مشاركة، إشعار تجريبي)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "${notif.title}\n${notif.message}\n🌿 عبر استوديو قبس")
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة الإشعار عبر"))
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مشاركة 📤", color = Color.White, fontFamily = CairoFont, fontSize = 11.5.sp)
                }

                if (notif.isTrendAlert) {
                    Button(
                        onClick = onOpenContentGuard,
                        modifier = Modifier.weight(1.2f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مرصد الثغور 🛡️", color = Color.White, fontFamily = CairoFont, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
