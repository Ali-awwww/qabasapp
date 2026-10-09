package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.qabas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accountService = remember { AppServices.getAccountService(context) }
    val giftManager = remember { AppServices.getGiftManager(context) }
    
    var balance by remember { mutableStateOf(accountService.walletBalance) }
    val tabs = listOf("المحفظة والمهام", "متجر قبس", "مركز الهدايا")
    var selectedTabIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(Translator.tr("مركز مكافآت قبس"), fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = DeepSlate,
                    contentColor = GoldPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = GoldPrimary,
                            height = 3.dp
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    title, 
                                    fontFamily = CairoFont, 
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == index) GoldPrimary else TextSecondary
                                ) 
                            }
                        )
                    }
                }
            }
        },
        containerColor = DeepSlate
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Crossfade(targetState = selectedTabIndex, label = "Tab Transition") { index ->
                when (index) {
                    0 -> WalletAndTasksTab(accountService, balance) { newBalance -> balance = newBalance }
                    1 -> StoreTab(accountService, balance) { newBalance -> balance = newBalance }
                    2 -> GiftCenterTab(giftManager) { newBalance -> balance = newBalance; accountService.walletBalance = newBalance }
                }
            }
        }
    }
}

@Composable
fun WalletAndTasksTab(accountService: AccountService, balance: Int, onBalanceChange: (Int) -> Unit) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = dateFormat.format(Date())
    var canCheckin by remember { mutableStateOf(accountService.lastCheckinDate != today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Balance Display
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2C3E50), Color(0xFF0B0F19))), RoundedCornerShape(24.dp))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Toll, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$balance", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                    Text("نقطة ذهبية", color = GoldPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = TajawalFont)
                }
            }
        }

        // Daily Check-in
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (canCheckin) GoldPrimary else Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp).background(if (canCheckin) GoldPrimary.copy(alpha = 0.2f) else Color(0xFF1E293B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = if (canCheckin) GoldPrimary else Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("المكافأة اليومية", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(if (canCheckin) "احصل على +20 نقطة الآن!" else "عد غداً لمكافأة جديدة", color = if (canCheckin) GoldPrimary else TextSecondary, fontFamily = NotoSansFont, fontSize = 14.sp)
                        }
                    }
                    Button(
                        onClick = {
                            if (canCheckin) {
                                val newBalance = balance + 20
                                accountService.walletBalance = newBalance
                                accountService.lastCheckinDate = today
                                onBalanceChange(newBalance)
                                canCheckin = false
                                android.widget.Toast.makeText(context, "حصلت على 20 نقطة! 🎉", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = canCheckin,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, disabledContainerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (canCheckin) "جمع" else "جُمعت", color = if (canCheckin) DeepSlate else Color.Gray, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("مهام الإنجاز 🎯", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            TaskRow("تصدير فيديو نهائي", "+50", Icons.Default.MovieCreation, "مكتملة 0/1")
            TaskRow("دعوة صديق لتطبيق قبس", "+100", Icons.Default.PersonAdd, "مكتملة 0/5")
            TaskRow("تقييم التطبيق بـ 5 نجوم", "+200", Icons.Default.Star, "متاحة")
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TaskRow(title: String, points: String, icon: androidx.compose.ui.graphics.vector.ImageVector, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, color = Color.White, fontFamily = TajawalFont, fontSize = 16.sp)
                    Text(status, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                }
            }
            Surface(color = Color(0xFF10B981).copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                Text(points, color = Color(0xFF10B981), fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
fun StoreTab(accountService: AccountService, balance: Int, onBalanceChange: (Int) -> Unit) {
    val context = LocalContext.current
    var showRedeemDialog by remember { mutableStateOf<RewardItem?>(null) }

    val storeItems = listOf(
        RewardItem("smart_dir", "المخرج الذكي", 100, Icons.Default.AutoAwesome, "توليد فكرة متكاملة بضغطة زر"),
        RewardItem("no_watermark", "إزالة العلامة", 150, Icons.Default.WaterDrop, "إزالة العلامة المائية لفيديو واحد"),
        RewardItem("pro_day", "قبس برو (يوم)", 500, Icons.Default.WorkspacePremium, "فتح جميع الميزات الاحترافية لمدة 24 ساعة"),
        RewardItem("pro_week", "قبس برو (أسبوع)", 3000, Icons.Default.WorkspacePremium, "فتح جميع الميزات الاحترافية لمدة 7 أيام")
    )

    showRedeemDialog?.let { reward ->
        AlertDialog(
            onDismissRequest = { showRedeemDialog = null },
            containerColor = DeepSlate,
            title = { Text("تأكيد الشراء", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("شراء '${reward.title}' مقابل ${reward.cost} نقطة؟", color = TextSecondary, fontFamily = TajawalFont, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("الرصيد بعد الشراء: ${balance - reward.cost} نقطة", color = if (balance >= reward.cost) GoldPrimary else Color.Red, fontFamily = NotoSansFont, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (balance >= reward.cost) {
                            val newBalance = balance - reward.cost
                            accountService.walletBalance = newBalance
                            onBalanceChange(newBalance)
                            if (reward.id.startsWith("pro_")) accountService.isPremium = true
                            android.widget.Toast.makeText(context, "تم الشراء بنجاح! 🎉", android.widget.Toast.LENGTH_SHORT).show()
                            showRedeemDialog = null
                        } else {
                            android.widget.Toast.makeText(context, "رصيدك لا يكفي!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) { Text("شراء", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRedeemDialog = null }) { Text("إلغاء", color = Color.White, fontFamily = CairoFont) }
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(storeItems) { item ->
            StoreItemCard(item = item, canAfford = balance >= item.cost) { showRedeemDialog = item }
        }
    }
}

data class RewardItem(val id: String, val title: String, val cost: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val desc: String)

@Composable
fun StoreItemCard(item: RewardItem, canAfford: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.85f).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (canAfford) GoldPrimary.copy(alpha = 0.5f) else Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(item.icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(40.dp).padding(top = 8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.title, color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.desc, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
            Surface(
                color = if (canAfford) GoldPrimary else Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    "${item.cost} نقطة",
                    color = if (canAfford) DeepSlate else Color.Gray,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun GiftCenterTab(giftManager: GiftManager, onBalanceUpdated: (Int) -> Unit) {
    val context = LocalContext.current
    var codeInput by remember { mutableStateOf("") }
    var activePromoDate by remember { mutableStateOf(giftManager.getPromoExpiryDateString()) }
    val accountService = remember { AppServices.getAccountService(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("استرداد كود الهدايا", color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("أدخل كود الترقية أو بطاقة شحن النقاط للحصول على المكافأة", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase() },
                        placeholder = { Text("أدخل الكود هنا (مثال: QABAS-PRO)", fontFamily = NotoSansFont, color = Color.Gray, fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            if (codeInput.isNotBlank()) {
                                val result = giftManager.redeemCode(codeInput)
                                val msg = when (result) {
                                    RedemptionResult.SUCCESS_PROMO -> {
                                        activePromoDate = giftManager.getPromoExpiryDateString()
                                        "تم تفعيل باقة برو بنجاح! 🎉"
                                    }
                                    RedemptionResult.SUCCESS_GIFT_CARD -> {
                                        onBalanceUpdated(accountService.walletBalance)
                                        "تم شحن رصيدك بنجاح! 💰"
                                    }
                                    RedemptionResult.INVALID_CODE -> "عذراً، الكود غير صحيح أو منتهي."
                                    RedemptionResult.ALREADY_USED -> "لقد قمت باستخدام هذا الكود مسبقاً!"
                                    RedemptionResult.EXPIRED -> "عذراً، انتهت صلاحية هذا الكود."
                                }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                if (result == RedemptionResult.SUCCESS_PROMO || result == RedemptionResult.SUCCESS_GIFT_CARD) {
                                    codeInput = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("استرداد الآن", color = DeepSlate, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        if (activePromoDate != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("باقة قبس برو مفعلة!", color = Color(0xFF10B981), fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("صالحة حتى: $activePromoDate", color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
