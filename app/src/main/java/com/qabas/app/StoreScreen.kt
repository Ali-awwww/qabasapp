package com.qabas.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

enum class StoreItemType { PRO_SUB, COINS }

data class StoreItem(
    val id: String,
    val title: String,
    val description: String,
    val price: String,
    val amount: Int = 0,
    val type: StoreItemType,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isPopular: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accountService = remember { AppServices.getAccountService(context) }
    
    // Initialize real BillingManager
    val billingManager = remember { BillingManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    
    val storeItems = listOf(
        StoreItem("pro_1m", "قبس برو - شهري", "توليد لا محدود + تصدير 4K + إزالة العلامة المائية", "4.99$", type = StoreItemType.PRO_SUB, icon = Icons.Default.WorkspacePremium),
        StoreItem("pro_1y", "قبس برو - سنوي", "خصم 40% على الباقة السنوية + جميع ميزات برو", "39.99$", type = StoreItemType.PRO_SUB, icon = Icons.Default.WorkspacePremium, isPopular = true),
        StoreItem("coin_500", "500 نقطة", "رصيد سريع لإنتاج المحتوى", "0.99$", 500, StoreItemType.COINS, Icons.Default.Toll),
        StoreItem("coin_1200", "1200 نقطة", "الأكثر مبيعاً (+200 مجاناً)", "1.99$", 1200, StoreItemType.COINS, Icons.Default.Toll, isPopular = true),
        StoreItem("coin_3000", "3000 نقطة", "قيمة استثنائية لصناع المحتوى", "4.99$", 3000, StoreItemType.COINS, Icons.Default.Toll),
        StoreItem("coin_10000", "10,000 نقطة", "صندوق الكنز (+2000 مجاناً)", "14.99$", 10000, StoreItemType.COINS, Icons.Default.MonetizationOn)
    )

    var balance by remember { mutableIntStateOf(accountService.walletBalance) }
    var isUserPremium by remember { mutableStateOf(accountService.isPremium || accountService.isDeveloperOrAdmin || accountService.hasCustomKeys) }

    var showPurchaseDialog by remember { mutableStateOf<StoreItem?>(null) }
    var isProcessingPurchase by remember { mutableStateOf(false) }

    fun resetPurchaseState() {
        isProcessingPurchase = false
        showPurchaseDialog = null
    }

    // Set callbacks
    DisposableEffect(billingManager) {
        billingManager.onPurchaseSuccess = { productId ->
            val purchasedItem = storeItems.find { it.id == productId }
            if (purchasedItem != null) {
                if (purchasedItem.type == StoreItemType.COINS) {
                    val newBalance = accountService.walletBalance + purchasedItem.amount
                    accountService.walletBalance = newBalance
                    balance = newBalance
                } else {
                    accountService.isPremium = true
                    isUserPremium = true
                }
                android.widget.Toast.makeText(context, "تمت عملية الشراء بنجاح! 🎉", android.widget.Toast.LENGTH_LONG).show()
            }
            resetPurchaseState()
        }
        billingManager.onPurchaseCanceled = {
            android.widget.Toast.makeText(context, "تم إلغاء عملية الشراء", android.widget.Toast.LENGTH_SHORT).show()
            resetPurchaseState()
        }
        billingManager.onPurchaseError = { errorMessage ->
            android.widget.Toast.makeText(context, "فشل إتمام عملية الشراء: $errorMessage", android.widget.Toast.LENGTH_LONG).show()
            resetPurchaseState()
        }
        onDispose {
            billingManager.onPurchaseSuccess = null
            billingManager.onPurchaseCanceled = null
            billingManager.onPurchaseError = null
        }
    }

    showPurchaseDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!isProcessingPurchase) showPurchaseDialog = null },
            containerColor = DeepSlate,
            title = {
                Text(if (isProcessingPurchase) "جاري معالجة الدفع..." else "تأكيد الشراء", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            },
            text = {
                if (isProcessingPurchase) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoldPrimary)
                    }
                } else {
                    Column {
                        Text("سيتم الخصم من حسابك (Google Play) لشراء:", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(item.icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(item.title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                        Text(item.price, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isProcessingPurchase) {
                    Button(
                        onClick = {
                            isProcessingPurchase = true
                            val activity = context as? Activity
                            if (activity != null) {
                                billingManager.launchBillingFlow(
                                    activity = activity,
                                    productId = item.id,
                                    onFallbackSuccess = {
                                        val purchasedItem = storeItems.find { it.id == item.id }
                                        if (purchasedItem != null) {
                                            if (purchasedItem.type == StoreItemType.COINS) {
                                                val newBalance = accountService.walletBalance + purchasedItem.amount
                                                accountService.walletBalance = newBalance
                                                balance = newBalance
                                            } else {
                                                accountService.isPremium = true
                                                isUserPremium = true
                                            }
                                            android.widget.Toast.makeText(context, "تمت عملية الشراء بنجاح! 🎉", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                        resetPurchaseState()
                                    }
                                )
                                android.widget.Toast.makeText(context, "جارٍ فتح نافذة الدفع…", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                isProcessingPurchase = false
                                android.widget.Toast.makeText(context, "لا يمكن فتح نافذة الدفع على هذا الجهاز", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("شراء بنقرة واحدة", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isProcessingPurchase) {
                    TextButton(onClick = { showPurchaseDialog = null }) {
                        Text("إلغاء", color = Color.White, fontFamily = CairoFont)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.tr("المتجر المميز 🛒"), fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val hasActive = billingManager.checkActiveSubscriptions()
                            if (hasActive || accountService.isPremium) {
                                accountService.isPremium = true
                                isUserPremium = true
                                android.widget.Toast.makeText(context, "تمت استعادة اشتراك برو بنجاح! 👑", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "لا توجد اشتراكات سابقة نشطة لهذا الحساب", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Restore, contentDescription = "استعادة المشتريات", tint = GoldPrimary)
                    }
                    
                    Surface(
                        color = Color(0xFF141C27),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 12.dp).border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Toll, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("$balance", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSlate)
            )
        },
        containerColor = DeepSlate
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Subscription Section Header
            item(span = { GridItemSpan(2) }) {
                Text("باقات قبس برو 👑", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            
            // Subscriptions
            items(storeItems.filter { it.type == StoreItemType.PRO_SUB }, span = { GridItemSpan(2) }) { item ->
                ProSubscriptionCard(item = item, onClick = { showPurchaseDialog = item })
            }
            
            // Divider / Spacer
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("شحن النقاط الذهبية 💰", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            
            // Coins
            items(storeItems.filter { it.type == StoreItemType.COINS }) { item ->
                CoinStoreCard(item = item, onClick = { showPurchaseDialog = item })
            }
        }
    }
}

@Composable
fun ProSubscriptionCard(item: StoreItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "scale")

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "glow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .shadow(if (item.isPopular) 16.dp else 8.dp, RoundedCornerShape(20.dp), spotColor = if (item.isPopular) GoldPrimary else Color.Black)
            .border(
                width = if (item.isPopular) 2.dp else 1.dp,
                brush = if (item.isPopular) Brush.linearGradient(listOf(GoldPrimary.copy(alpha = glowAlpha), GoldPrimary)) else Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B))),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141C27)) // Very dark for contrast
    ) {
        Box {
            if (item.isPopular) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).background(Color(0xFFE53935), RoundedCornerShape(bottomEnd = 12.dp)).padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("الأكثر توفيراً 🔥", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Row(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(item.title, color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(item.description, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 14.sp)
                }
                
                Surface(
                    color = GoldPrimary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        item.price,
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CoinStoreCard(item: StoreItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, label = "scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .shadow(if (item.isPopular) 12.dp else 4.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.isPopular) GoldPrimary else Color(0xFF1E293B))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.isPopular) {
                Box(modifier = Modifier.fillMaxWidth().background(GoldPrimary).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Text("شائع جداً", color = DeepSlate, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(if (item.isPopular) 16.dp else 0.dp))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(GoldPrimary.copy(alpha = 0.3f), Color.Transparent)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.title, color = Color.White, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(item.description, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.weight(1f))
                
                Surface(
                    color = Color(0xFF141C27),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        item.price,
                        color = GoldPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}
