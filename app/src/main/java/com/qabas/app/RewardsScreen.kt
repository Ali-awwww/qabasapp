package com.qabas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.*
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = QabasTheme.current
    val accountService = remember { AppServices.getAccountService(context) }
    val giftManager = remember { AppServices.getGiftManager(context) }
    var balance by remember { mutableIntStateOf(accountService.walletBalance) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = theme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            Column(modifier = Modifier.background(theme.background)) {
                AppleTopBar(
                    title = Translator.tr("مركز مكافآت قبس"),
                    subtitle = "$balance نقطة متاحة",
                    onBack = onBack
                )
                AppleSegmentedControl(
                    items = listOf(
                        0 to "المحفظة والمهام",
                        1 to "متجر قبس",
                        2 to "مركز الهدايا"
                    ),
                    selectedItem = selectedTabIndex,
                    onSelect = { selectedTabIndex = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(theme.background)
        ) {
            Crossfade(targetState = selectedTabIndex, label = "RewardsTabTransition") { index ->
                when (index) {
                    0 -> WalletAndTasksTab(accountService, balance) { newBalance -> balance = newBalance }
                    1 -> StoreTab(accountService, balance) { newBalance -> balance = newBalance }
                    2 -> GiftCenterTab(giftManager) { newBalance ->
                        balance = newBalance
                        accountService.walletBalance = newBalance
                    }
                }
            }
        }
    }
}

@Composable
fun WalletAndTasksTab(
    accountService: AccountService,
    balance: Int,
    onBalanceChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val prefs = remember { context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = remember { dateFormat.format(Date()) }
    var canCheckin by remember { mutableStateOf(accountService.lastCheckinDate != today) }

    // Real milestone progress from local projects & persisted user actions
    var completedExportCount by remember { mutableIntStateOf(accountService.dailyVideoCount) }
    LaunchedEffect(balance) {
        val count = runCatching {
            withContext(Dispatchers.IO) {
                ProjectService(context).getLocalProjects().count {
                    !it.status.equals("DRAFT", ignoreCase = true) && !it.status.contains("مسودة")
                }
            }
        }.getOrDefault(0).coerceAtLeast(accountService.dailyVideoCount)
        completedExportCount = count
    }
    var exportRewardClaimed by remember { mutableStateOf(prefs.getBoolean("reward_task_export_claimed", false)) }
    var inviteCount by remember { mutableIntStateOf(prefs.getInt("reward_task_invite_count", 0)) }
    var rateRewardClaimed by remember { mutableStateOf(prefs.getBoolean("reward_task_rate_claimed", false)) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AppleCard(cornerRadius = 18.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(theme.warning.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Toll,
                            contentDescription = null,
                            tint = theme.warning,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "$balance",
                        color = theme.textPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CairoFont
                    )
                    Text(
                        text = "نقطة مكافآت قبس",
                        color = theme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CairoFont
                    )
                }
            }
        }

        item {
            AppleCard {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canCheckin) theme.warning.copy(alpha = 0.14f)
                                    else theme.secondarySurface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = if (canCheckin) theme.warning else theme.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "المكافأة اليومية",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (canCheckin) "احصل على +20 نقطة الآن" else "عد غداً لمكافأة جديدة",
                                color = if (canCheckin) theme.primary else theme.textSecondary,
                                fontFamily = NotoSansFont,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            if (canCheckin) {
                                val newBalance = balance + 20
                                accountService.walletBalance = newBalance
                                accountService.lastCheckinDate = today
                                onBalanceChange(newBalance)
                                canCheckin = false
                                Toast.makeText(context, "حصلت على 20 نقطة", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = canCheckin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.primary,
                            contentColor = theme.onPrimary,
                            disabledContainerColor = theme.secondarySurface,
                            disabledContentColor = theme.textSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = if (canCheckin) "جمع" else "جُمعت",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            AppleGroupedSection(
                header = "مهام الإنجاز",
                footer = "تُحتسب المهام بناءً على نشاطك الفعلي في تصدير المقاطع ومشاركة التطبيق."
            ) {
                val exportReady = completedExportCount > 0 && !exportRewardClaimed
                TaskRow(
                    title = "تصدير فيديو نهائي",
                    points = "+50",
                    icon = Icons.Default.MovieCreation,
                    status = when {
                        exportRewardClaimed -> "تم التحصيل ✓"
                        completedExportCount > 0 -> "جاهز للاستلام ($completedExportCount/1)"
                        else -> "أنشئ وصدّر مقطعاً أولاً ($completedExportCount/1)"
                    },
                    actionLabel = if (exportReady) "استلام" else null,
                    onClick = {
                        if (exportReady) {
                            val updated = accountService.walletBalance + 50
                            accountService.walletBalance = updated
                            prefs.edit().putBoolean("reward_task_export_claimed", true).apply()
                            exportRewardClaimed = true
                            onBalanceChange(updated)
                            Toast.makeText(context, "تم إضافة 50 نقطة إلى رصيدك", Toast.LENGTH_SHORT).show()
                        } else if (!exportRewardClaimed) {
                            Toast.makeText(context, "قم بتصدير مشروع واحد من الاستوديو أولاً", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                AppleInsetDivider()

                TaskRow(
                    title = "دعوة صديق لتطبيق قبس",
                    points = "+20",
                    icon = Icons.Default.PersonAdd,
                    status = "تمت الدعوة ${inviteCount.coerceAtMost(5)}/5",
                    actionLabel = if (inviteCount < 5) "دعوة" else null,
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "اكتشف استوديو قبس لصناعة المحتوى الإسلامي والمصحف الشريف وتفسير الآيات."
                            )
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(shareIntent, "دعوة صديق إلى قبس"))
                            if (inviteCount < 5) {
                                inviteCount += 1
                                prefs.edit().putInt("reward_task_invite_count", inviteCount).apply()
                                val updated = accountService.walletBalance + 20
                                accountService.walletBalance = updated
                                onBalanceChange(updated)
                            }
                        }
                    }
                )

                AppleInsetDivider()

                TaskRow(
                    title = "تقييم التطبيق على المتجر",
                    points = "+100",
                    icon = Icons.Default.Star,
                    status = if (rateRewardClaimed) "شكراً لتقييمك قبس ✓" else "قيّم التطبيق في متجر Google Play",
                    actionLabel = if (!rateRewardClaimed) "تقييم" else null,
                    onClick = {
                        val uri = Uri.parse("market://details?id=${context.packageName}")
                        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                        val launched = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            true
                        }.getOrElse {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                true
                            }.getOrDefault(false)
                        }
                        if (launched && !rateRewardClaimed) {
                            prefs.edit().putBoolean("reward_task_rate_claimed", true).apply()
                            rateRewardClaimed = true
                            val updated = accountService.walletBalance + 100
                            accountService.walletBalance = updated
                            onBalanceChange(updated)
                            Toast.makeText(context, "تم تسجيل المكافأة (+100 نقطة)", Toast.LENGTH_SHORT).show()
                        } else if (!launched) {
                            Toast.makeText(context, "تعذر فتح متجر التطبيقات على هذا الجهاز", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun TaskRow(
    title: String,
    points: String,
    icon: ImageVector,
    status: String,
    actionLabel: String? = null,
    onClick: () -> Unit = {}
) {
    val theme = QabasTheme.current
    AppleListRow(
        title = title,
        subtitle = status,
        icon = icon,
        iconBackground = theme.primary,
        showChevron = false,
        onClick = onClick,
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = theme.success.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = points,
                        color = theme.success,
                        fontFamily = RobotoMonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (actionLabel != null) {
                    Text(
                        text = actionLabel,
                        color = theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    )
}

@Composable
fun StoreTab(
    accountService: AccountService,
    balance: Int,
    onBalanceChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    var showRedeemDialog by remember { mutableStateOf<RewardItem?>(null) }

    val storeItems = remember {
        listOf(
            RewardItem("smart_dir", "المخرج الذكي", 100, Icons.Default.AutoAwesome, "توليد فكرة متكاملة بضغطة زر"),
            RewardItem("no_watermark", "إزالة العلامة", 150, Icons.Default.WaterDrop, "إزالة العلامة المائية لفيديو واحد"),
            RewardItem("pro_day", "قبس برو (يوم)", 500, Icons.Default.WorkspacePremium, "فتح جميع الميزات لمدة 24 ساعة"),
            RewardItem("pro_week", "قبس برو (أسبوع)", 3000, Icons.Default.WorkspacePremium, "فتح جميع الميزات لمدة 7 أيام")
        )
    }

    showRedeemDialog?.let { reward ->
        AlertDialog(
            onDismissRequest = { showRedeemDialog = null },
            containerColor = theme.cardSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    text = "تأكيد الاستبدال",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "استبدال «${reward.title}» مقابل ${reward.cost} نقطة؟",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "الرصيد بعد الاستبدال: ${balance - reward.cost} نقطة",
                        color = if (balance >= reward.cost) theme.primary else theme.destructive,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (balance >= reward.cost) {
                            val newBalance = balance - reward.cost
                            accountService.walletBalance = newBalance
                            onBalanceChange(newBalance)
                            if (reward.id.startsWith("pro_")) {
                                accountService.isPremium = true
                            }
                            Toast.makeText(context, "تم الاستبدال بنجاح", Toast.LENGTH_SHORT).show()
                            showRedeemDialog = null
                        } else {
                            Toast.makeText(context, "رصيدك لا يكفي", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = balance >= reward.cost,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("استبدال", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRedeemDialog = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(storeItems) { item ->
            StoreItemCard(item = item, canAfford = balance >= item.cost) {
                showRedeemDialog = item
            }
        }
    }
}

data class RewardItem(
    val id: String,
    val title: String,
    val cost: Int,
    val icon: ImageVector,
    val desc: String
)

@Composable
fun StoreItemCard(item: RewardItem, canAfford: Boolean, onClick: () -> Unit) {
    val theme = QabasTheme.current
    AppleCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(theme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = item.title,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.desc,
                color = theme.textSecondary,
                fontFamily = NotoSansFont,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )

            Surface(
                color = if (canAfford) theme.primary else theme.secondarySurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${item.cost} نقطة",
                        color = if (canAfford) theme.onPrimary else theme.textSecondary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GiftCenterTab(
    giftManager: GiftManager,
    onBalanceUpdated: (Int) -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()
    var isRedeeming by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    var activePromoDate by remember { mutableStateOf(giftManager.getPromoExpiryDateString()) }
    val accountService = remember { AppServices.getAccountService(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AppleCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "استرداد كود الهدايا",
                        color = theme.textPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "أدخل كود الترقية أو بطاقة شحن النقاط للحصول على المكافأة",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase() },
                        placeholder = {
                            Text(
                                text = "أدخل الكود هنا (مثال: QABAS-PRO)",
                                fontFamily = NotoSansFont,
                                color = theme.textSecondary,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.separator,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ApplePrimaryButton(
                        text = "استرداد الآن",
                        isLoading = isRedeeming,
                        enabled = codeInput.isNotBlank(),
                        onClick = {
                            if (codeInput.isNotBlank() && !isRedeeming) {
                                isRedeeming = true
                                coroutineScope.launch {
                                    try {
                                        val result = giftManager.redeemCode(codeInput)
                                        val msg = when (result) {
                                            RedemptionResult.SUCCESS_PROMO -> {
                                                activePromoDate = giftManager.getPromoExpiryDateString()
                                                "تم تفعيل باقة برو بنجاح"
                                            }
                                            RedemptionResult.SUCCESS_GIFT_CARD -> {
                                                onBalanceUpdated(accountService.walletBalance)
                                                "تم شحن رصيدك بنجاح"
                                            }
                                            RedemptionResult.INVALID_CODE -> "عذراً، الكود غير صحيح أو غير مفعل."
                                            RedemptionResult.ALREADY_USED -> "لقد قمت باستخدام هذا الكود بالحد الأقصى المسموح."
                                            RedemptionResult.EXPIRED -> "عذراً، انتهت صلاحية هذا الكود."
                                            RedemptionResult.LIMIT_REACHED -> "عذراً، تم استنفاد الحد الأقصى لاستخدام هذا الكود."
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        if (result == RedemptionResult.SUCCESS_PROMO || result == RedemptionResult.SUCCESS_GIFT_CARD) {
                                            codeInput = ""
                                        }
                                    } finally {
                                        isRedeeming = false
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        if (activePromoDate != null) {
            item {
                AppleCard(
                    containerColor = theme.success.copy(alpha = if (theme.isDark) 0.14f else 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = theme.success,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "باقة قبس برو مفعلة",
                                color = theme.success,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "صالحة حتى: $activePromoDate",
                                color = theme.textPrimary,
                                fontFamily = NotoSansFont,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
