package com.qabas.app

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleKeyRequiredBanner
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.TajawalFont
import kotlinx.coroutines.launch

enum class StoreItemType { PRO_SUB, COINS }

data class StoreItem(
    val id: String,
    val title: String,
    val description: String,
    val price: String,
    val amount: Int = 0,
    val type: StoreItemType,
    val icon: ImageVector,
    val isPopular: Boolean = false
)

@Composable
fun StoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val accountService = remember { AppServices.getAccountService(context) }
    val billingManager = remember { BillingManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val isBillingReady by billingManager.isReady.collectAsStateWithLifecycle()
    val productsMap by billingManager.products.collectAsStateWithLifecycle()
    val canPurchase = isBillingReady && productsMap.isNotEmpty()

    val storeItems = remember(productsMap) {
        listOf(
            StoreItem(BillingManager.SUB_MONTHLY_PRO, "خادم المسلم برو - شهري", "توليد لا محدود + تصدير 4K + إزالة العلامة المائية", billingManager.getFormattedPrice(BillingManager.SUB_MONTHLY_PRO, "4.99$"), type = StoreItemType.PRO_SUB, icon = Icons.Default.WorkspacePremium),
            StoreItem(BillingManager.SUB_ANNUAL_PRO, "خادم المسلم برو - سنوي", "خصم 40% على الباقة السنوية + جميع ميزات برو", billingManager.getFormattedPrice(BillingManager.SUB_ANNUAL_PRO, "39.99$"), type = StoreItemType.PRO_SUB, icon = Icons.Default.WorkspacePremium, isPopular = true),
            StoreItem(BillingManager.COIN_500, "500 نقطة", "رصيد سريع لإنتاج المحتوى", billingManager.getFormattedPrice(BillingManager.COIN_500, "0.99$"), 500, StoreItemType.COINS, Icons.Default.Toll),
            StoreItem(BillingManager.COIN_1200, "1200 نقطة", "الأكثر مبيعاً (+200 مجاناً)", billingManager.getFormattedPrice(BillingManager.COIN_1200, "1.99$"), 1200, StoreItemType.COINS, Icons.Default.Toll, isPopular = true),
            StoreItem(BillingManager.COIN_3000, "3000 نقطة", "قيمة استثنائية لصناع المحتوى", billingManager.getFormattedPrice(BillingManager.COIN_3000, "4.99$"), 3000, StoreItemType.COINS, Icons.Default.Toll),
            StoreItem(BillingManager.COIN_10000, "10,000 نقطة", "صندوق الكنز (+2000 مجاناً)", billingManager.getFormattedPrice(BillingManager.COIN_10000, "14.99$"), 10000, StoreItemType.COINS, Icons.Default.MonetizationOn)
        )
    }

    var balance by remember { mutableIntStateOf(accountService.walletBalance) }
    var showPurchaseDialog by remember { mutableStateOf<StoreItem?>(null) }
    var isProcessingPurchase by remember { mutableStateOf(false) }

    fun resetPurchaseState() {
        isProcessingPurchase = false
        showPurchaseDialog = null
    }

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
                }
                Toast.makeText(context, "تمت عملية الشراء بنجاح", Toast.LENGTH_LONG).show()
            }
            resetPurchaseState()
        }
        billingManager.onPurchaseCanceled = {
            Toast.makeText(context, "تم إلغاء عملية الشراء", Toast.LENGTH_SHORT).show()
            resetPurchaseState()
        }
        billingManager.onPurchaseError = { errorMessage ->
            Toast.makeText(context, "تعذر إتمام الشراء: $errorMessage", Toast.LENGTH_LONG).show()
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
            containerColor = theme.cardSurface,
            title = {
                Text(
                    if (isProcessingPurchase) "جارٍ معالجة الدفع..." else "تأكيد الشراء",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (isProcessingPurchase) {
                    Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.primary)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("سيتم الشراء عبر حسابك في Google Play:", color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 14.sp)
                        AppleCard(containerColor = theme.secondarySurface) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(item.icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(item.title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                                    Text(item.price, color = theme.primary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
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
                            val activity = context as? Activity
                            if (activity != null) {
                                isProcessingPurchase = true
                                billingManager.launchBillingFlow(activity = activity, productId = item.id)
                            } else {
                                Toast.makeText(context, "لا يمكن فتح نافذة الدفع على هذا الجهاز", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = canPurchase,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
                    ) {
                        Text("شراء الآن", fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isProcessingPurchase) {
                    TextButton(onClick = { showPurchaseDialog = null }) {
                        Text("إلغاء", color = theme.textSecondary, fontFamily = CairoFont)
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = Translator.tr("المتجر"),
                onBack = onBack,
                backTestTag = "store_back_button",
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val hasActive = billingManager.checkActiveSubscriptions()
                                if (hasActive || accountService.isPremium) {
                                    accountService.isPremium = true
                                    Toast.makeText(context, "تمت استعادة اشتراك برو بنجاح", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "لا توجد اشتراكات نشطة في Google Play", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = isBillingReady
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = "استعادة المشتريات",
                            tint = if (isBillingReady) theme.primary else theme.textSecondary
                        )
                    }
                    Surface(
                        color = theme.secondarySurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Toll, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("$balance", color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!canPurchase) {
                item(span = { GridItemSpan(2) }) {
                    AppleKeyRequiredBanner(
                        serviceName = Translator.tr("متجر Google Play"),
                        reason = Translator.tr("خدمة الفوترة غير متصلة أو منتجات المتجر غير مفعّلة في Google Play Console بعد.")
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                Text(
                    "باقات خادم المسلم برو",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(storeItems.filter { it.type == StoreItemType.PRO_SUB }, span = { GridItemSpan(2) }) { item ->
                ProSubscriptionCard(
                    item = item,
                    enabled = canPurchase,
                    onClick = { showPurchaseDialog = item }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Text(
                    "شحن النقاط",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(storeItems.filter { it.type == StoreItemType.COINS }) { item ->
                CoinStoreCard(
                    item = item,
                    enabled = canPurchase,
                    onClick = { showPurchaseDialog = item }
                )
            }
        }
    }
}

@Composable
fun ProSubscriptionCard(item: StoreItem, enabled: Boolean = true, onClick: () -> Unit) {
    val theme = QabasTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.98f else 1f, label = "scale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = theme.cardSurface,
        border = BorderStroke(if (item.isPopular) 1.5.dp else 0.5.dp, if (item.isPopular) theme.primary else theme.separator.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(item.title, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (item.isPopular) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = theme.primary.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "الأكثر توفيراً",
                                color = theme.primary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(item.description, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                color = theme.primary,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    item.price,
                    color = theme.onPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun CoinStoreCard(item: StoreItem, enabled: Boolean = true, onClick: () -> Unit) {
    val theme = QabasTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.96f else 1f, label = "scale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.84f)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.55f)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
        color = theme.cardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (item.isPopular) 1.5.dp else 0.5.dp, if (item.isPopular) theme.primary else theme.separator.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(theme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = theme.primary, modifier = Modifier.size(26.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.title, color = theme.textPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(item.description, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    item.price,
                    color = theme.primary,
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
