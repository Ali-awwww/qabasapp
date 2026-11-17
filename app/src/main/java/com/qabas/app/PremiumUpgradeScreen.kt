package com.qabas.app

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
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
import com.qabas.app.ui.components.AppleKeyRequiredBanner
import com.qabas.app.ui.components.ApplePrimaryButton
import com.qabas.app.ui.components.AppleTopBar
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import com.qabas.app.ui.theme.TajawalFont

@Composable
fun PremiumUpgradeScreen(
    onBack: () -> Unit,
    onUpgraded: () -> Unit
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val billingManager = remember { BillingManager.getInstance(context) }
    val accountService = remember { AppServices.getAccountService(context) }

    var selectedPlan by remember { mutableStateOf(BillingManager.SUB_ANNUAL_PRO) }
    var isProcessing by remember { mutableStateOf(false) }

    val productsMap by billingManager.products.collectAsStateWithLifecycle()
    val isBillingReady by billingManager.isReady.collectAsStateWithLifecycle()
    val isBillingLoading by billingManager.isLoading.collectAsStateWithLifecycle()
    val canPurchase = isBillingReady && productsMap.isNotEmpty()

    val monthlyPrice = remember(productsMap) {
        billingManager.getFormattedPrice(BillingManager.SUB_MONTHLY_PRO, "4.99$ / شهر")
    }
    val annualPrice = remember(productsMap) {
        billingManager.getFormattedPrice(BillingManager.SUB_ANNUAL_PRO, "39.99$ / سنة")
    }

    DisposableEffect(billingManager) {
        billingManager.onPurchaseSuccess = { _ ->
            isProcessing = false
            accountService.isPremium = true
            Toast.makeText(context, Translator.tr("تم تفعيل اشتراك قبس برو بنجاح"), Toast.LENGTH_LONG).show()
            onUpgraded()
        }
        billingManager.onPurchaseCanceled = {
            isProcessing = false
            Toast.makeText(context, Translator.tr("تم إلغاء عملية الاشتراك"), Toast.LENGTH_SHORT).show()
        }
        billingManager.onPurchaseError = { errorMsg ->
            isProcessing = false
            Toast.makeText(context, Translator.tr("تعذر الاشتراك: $errorMsg"), Toast.LENGTH_SHORT).show()
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
                title = Translator.tr("ترقية الاشتراك"),
                onBack = onBack,
                backTestTag = "premium_back_button",
                actions = {
                    TextButton(
                        onClick = {
                            billingManager.restorePurchases { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                if (success) onUpgraded()
                            }
                        },
                        enabled = isBillingReady && !isBillingLoading && !isProcessing,
                        modifier = Modifier.testTag("restore_purchases_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = if (isBillingReady) theme.primary else theme.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = Translator.tr("استعادة"),
                            fontFamily = CairoFont,
                            color = if (isBillingReady) theme.primary else theme.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(theme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = Translator.tr("استوديو قبس برو"),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = theme.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = Translator.tr("توليد غير محدود عبر الذكاء الاصطناعي وتصدير سينمائي بدقة 4K بدون علامة مائية."),
                fontFamily = NotoSansFont,
                fontSize = 14.sp,
                color = theme.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            if (!canPurchase) {
                Spacer(modifier = Modifier.height(16.dp))
                AppleKeyRequiredBanner(
                    serviceName = Translator.tr("الاشتراك عبر Google Play"),
                    reason = Translator.tr("متجر Google Play Billing غير متصل أو لم يتم إعداد باقات الاشتراك لهذا الإصدار بعد.")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlanCard(
                    title = Translator.tr("الباقة السنوية"),
                    price = annualPrice,
                    subtext = Translator.tr("توفير 40%"),
                    isSelected = selectedPlan == BillingManager.SUB_ANNUAL_PRO,
                    isPopular = true,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedPlan = BillingManager.SUB_ANNUAL_PRO }
                )

                PlanCard(
                    title = Translator.tr("الباقة الشهرية"),
                    price = monthlyPrice,
                    subtext = Translator.tr("إلغاء في أي وقت"),
                    isSelected = selectedPlan == BillingManager.SUB_MONTHLY_PRO,
                    isPopular = false,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedPlan = BillingManager.SUB_MONTHLY_PRO }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            AppleCard(cornerRadius = 16.dp) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = Translator.tr("مميزات باقة قبس برو"),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FeatureRow(Translator.tr("صناعة وتوليد فيديوهات دعوية غير محدودة"))
                    FeatureRow(Translator.tr("وصول كامل للاستوديو الذكي والمونتاج الاحترافي"))
                    FeatureRow(Translator.tr("تصدير سينمائي بجودة 4K بدون علامة مائية"))
                    FeatureRow(Translator.tr("أولوية لمعالجة نماذج الذكاء الاصطناعي"))
                    FeatureRow(Translator.tr("استخدام الأصوات العصبية المتقدمة عند ربط المفتاح"))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ApplePrimaryButton(
                text = if (selectedPlan == BillingManager.SUB_ANNUAL_PRO) {
                    Translator.tr("اشترك سنوياً ووفر 40%")
                } else {
                    Translator.tr("اشترك شهرياً")
                },
                icon = Icons.Default.WorkspacePremium,
                isLoading = isProcessing || isBillingLoading,
                enabled = canPurchase && !isProcessing && !isBillingLoading,
                modifier = Modifier.testTag("subscribe_pro_button"),
                onClick = {
                    val activity = context as? Activity
                    if (activity == null) {
                        Toast.makeText(context, Translator.tr("لا يمكن فتح نافذة الدفع من هذا السياق"), Toast.LENGTH_SHORT).show()
                        return@ApplePrimaryButton
                    }
                    isProcessing = true
                    billingManager.launchBillingFlow(activity = activity, productId = selectedPlan)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = Translator.tr("تتم إدارة الاشتراك والدفع بأمان عبر Google Play"),
                fontFamily = NotoSansFont,
                color = theme.textSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    subtext: String,
    isSelected: Boolean,
    isPopular: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = QabasTheme.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) theme.primary.copy(alpha = 0.10f) else theme.cardSurface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 0.5.dp,
            color = if (isSelected) theme.primary else theme.separator.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isPopular) {
                Surface(
                    color = theme.primary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = Translator.tr("الأكثر توفيراً"),
                        color = theme.onPrimary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(text = title, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = price, fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = theme.primary, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtext, fontFamily = NotoSansFont, color = theme.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    val theme = QabasTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, fontFamily = NotoSansFont, color = theme.textPrimary, fontSize = 14.sp)
    }
}
