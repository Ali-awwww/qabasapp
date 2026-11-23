package com.qabas.app

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleGroupedSection
import com.qabas.app.ui.components.AppleKeyRequiredBanner
import com.qabas.app.ui.components.AppleListRow
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleSecondaryButton
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.RobotoMonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PaymentScreen(
    onPaymentSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = QabasTheme.current
    val paymentService = remember { AppServices.getPaymentService(context) }
    val accountService = remember { AppServices.getAccountService(context) }
    val billingManager = remember { BillingManager.getInstance(context) }

    val isBillingReady by billingManager.isReady.collectAsStateWithLifecycle()
    val productsMap by billingManager.products.collectAsStateWithLifecycle()

    val isProOrDev = accountService.isPremium || accountService.isDeveloperOrAdmin || accountService.hasCustomKeys
    val videoCost = if (isProOrDev) 0 else 5

    var currentBalance by remember { mutableIntStateOf(paymentService.userBalance) }
    var isProcessing by remember { mutableStateOf(false) }
    var paymentSuccess by remember { mutableStateOf(false) }
    var showRechargeDialog by remember { mutableStateOf(false) }

    val canPurchaseCoins = isBillingReady && productsMap.isNotEmpty()

    DisposableEffect(billingManager) {
        billingManager.onPurchaseSuccess = { productId ->
            isProcessing = false
            val coinsAdded = when (productId) {
                BillingManager.COIN_500 -> 500
                BillingManager.COIN_1200 -> 1200
                BillingManager.COIN_3000 -> 3000
                BillingManager.COIN_10000 -> 10000
                else -> 0
            }
            if (coinsAdded > 0) {
                scope.launch {
                    paymentService.recharge(coinsAdded)
                    currentBalance = paymentService.userBalance
                    showRechargeDialog = false
                    Toast.makeText(context, Translator.trf("تم شحن الرصيد بنجاح (+{0} عملة)", coinsAdded), Toast.LENGTH_LONG).show()
                }
            }
        }
        billingManager.onPurchaseCanceled = {
            isProcessing = false
            Toast.makeText(context, Translator.tr("تم إلغاء عملية الشراء"), Toast.LENGTH_SHORT).show()
        }
        billingManager.onPurchaseError = { msg ->
            isProcessing = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
        onDispose {
            billingManager.onPurchaseSuccess = null
            billingManager.onPurchaseCanceled = null
            billingManager.onPurchaseError = null
        }
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            AppleTopBar(
                title = Translator.tr("الدفع والتصدير"),
                onBack = onBack,
                backTestTag = "payment_back_button"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AppleCard(cornerRadius = 18.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isProOrDev) Icons.Default.WorkspacePremium else Icons.Default.Diamond,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isProOrDev) Translator.tr("تصدير فيديو بدقة عالية") else Translator.tr("تكلفة تصدير الفيديو"),
                        color = theme.textSecondary,
                        fontSize = 14.sp,
                        fontFamily = NotoSansFont
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isProOrDev) "0" else "$videoCost",
                            color = theme.primary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoMonoFont
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Diamond,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = if (isProOrDev) Translator.tr("ميزة مجانية لمشتركي خادم المسلم برو") else Translator.tr("عملات خادم المسلم"),
                        color = if (isProOrDev) theme.success else theme.primary,
                        fontSize = 13.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AppleGroupedSection(header = Translator.tr("الرصيد والفوترة")) {
                AppleListRow(
                    title = Translator.tr("رصيدك الحالي"),
                    icon = Icons.Default.AccountBalanceWallet,
                    iconBackground = theme.primary,
                    trailingText = "$currentBalance ${Translator.tr("عملة")}",
                    showChevron = false
                )
            }

            if (!isProOrDev && currentBalance < videoCost) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = theme.destructive.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = Translator.tr("رصيدك الحالي غير كافٍ لتصدير الفيديو. يرجى شحن الرصيد عبر Google Play للمتابعة."),
                        color = theme.destructive,
                        modifier = Modifier.padding(14.dp),
                        fontFamily = NotoSansFont,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }

                if (!canPurchaseCoins) {
                    AppleKeyRequiredBanner(
                        serviceName = Translator.tr("شحن الرصيد عبر Google Play"),
                        reason = Translator.tr("خدمة Google Play Billing غير متصلة أو منتجات المتجر غير متاحة على هذا الجهاز حالياً.")
                    )
                }

                AppleSecondaryButton(
                    text = Translator.tr("شحن الرصيد عبر Google Play"),
                    icon = Icons.Default.AccountBalanceWallet,
                    enabled = canPurchaseCoins,
                    onClick = { showRechargeDialog = true },
                    modifier = Modifier.testTag("open_recharge_button")
                )
            } else {
                if (paymentSuccess) {
                    AppleCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.success)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProOrDev) Translator.tr("تم التجهيز للتصدير") else Translator.tr("تم الدفع بنجاح"),
                                color = theme.success,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    ApplePrimaryButton(
                        text = if (isProOrDev) {
                            Translator.tr("تصدير الفيديو الآن (مجاناً لمشتركي برو)")
                        } else {
                            Translator.trf("تأكيد الدفع والتصدير (خصم {0} عملات)", videoCost)
                        },
                        isLoading = isProcessing,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("confirm_export_payment_button"),
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                val success = if (isProOrDev) true else paymentService.processPayment(videoCost)
                                if (success) {
                                    currentBalance = paymentService.userBalance
                                    paymentSuccess = true
                                    paymentService.generateInvoice(context, "PRJ-${System.currentTimeMillis()}", videoCost)
                                    if (!isProOrDev) {
                                        CloudServices.Database.recordPurchase("video_export_hd", "coin_spent_${System.currentTimeMillis()}")
                                    }
                                    delay(900)
                                    onPaymentSuccess()
                                } else {
                                    isProcessing = false
                                    Toast.makeText(context, Translator.tr("رصيدك غير كافٍ لإتمام التصدير"), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showRechargeDialog) {
        val coinPacks = listOf(
            Triple(BillingManager.COIN_500, 500, billingManager.getFormattedPrice(BillingManager.COIN_500, "$0.99")),
            Triple(BillingManager.COIN_1200, 1200, billingManager.getFormattedPrice(BillingManager.COIN_1200, "$1.99")),
            Triple(BillingManager.COIN_3000, 3000, billingManager.getFormattedPrice(BillingManager.COIN_3000, "$4.99")),
            Triple(BillingManager.COIN_10000, 10000, billingManager.getFormattedPrice(BillingManager.COIN_10000, "$14.99"))
        )
        AlertDialog(
            onDismissRequest = { if (!isProcessing) showRechargeDialog = false },
            containerColor = theme.cardSurface,
            title = {
                Text(
                    Translator.tr("شحن رصيد خادم المسلم"),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    coinPacks.forEachIndexed { idx, (sku, amount, formattedPrice) ->
                        RechargeOption(
                            amount = amount,
                            price = formattedPrice,
                            isPopular = idx == 1,
                            enabled = canPurchaseCoins && !isProcessing,
                            onClick = {
                                val activity = context as? Activity
                                if (activity == null) {
                                    Toast.makeText(context, Translator.tr("لا يمكن بدء الشراء خارج النشاط الحالي"), Toast.LENGTH_SHORT).show()
                                } else {
                                    isProcessing = true
                                    billingManager.launchBillingFlow(activity, sku)
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (!isProcessing) showRechargeDialog = false }) {
                    Text(Translator.tr("إغلاق"), color = theme.textSecondary, fontFamily = CairoFont)
                }
            }
        )
    }
}

@Composable
fun RechargeOption(
    amount: Int,
    price: String,
    isPopular: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    AppleCard(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        containerColor = if (isPopular) theme.primary.copy(alpha = 0.08f) else theme.secondarySurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Diamond, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$amount ${Translator.tr("عملة")}",
                    color = theme.textPrimary,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (isPopular) {
                    Text(Translator.tr("الأكثر طلباً"), color = theme.primary, fontSize = 11.sp, fontFamily = CairoFont)
                }
                Text(price, color = theme.textSecondary, fontFamily = RobotoMonoFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
