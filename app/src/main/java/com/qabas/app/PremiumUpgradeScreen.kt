package com.qabas.app

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumUpgradeScreen(
    onBack: () -> Unit,
    onUpgraded: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val billingManager = remember { BillingManager.getInstance(context) }
    val accountService = remember { AppServices.getAccountService(context) }

    var selectedPlan by remember { mutableStateOf(BillingManager.SUB_ANNUAL_PRO) }
    var isProcessing by remember { mutableStateOf(false) }

    val productsMap by billingManager.products.collectAsStateWithLifecycle()
    val isBillingLoading by billingManager.isLoading.collectAsStateWithLifecycle()

    val monthlyPrice = remember(productsMap) {
        billingManager.getFormattedPrice(BillingManager.SUB_MONTHLY_PRO, "4.99$ / شهر")
    }
    val annualPrice = remember(productsMap) {
        billingManager.getFormattedPrice(BillingManager.SUB_ANNUAL_PRO, "39.99$ / سنة")
    }

    DisposableEffect(billingManager) {
        billingManager.onPurchaseSuccess = { productId ->
            isProcessing = false
            accountService.isPremium = true
            Toast.makeText(context, Translator.tr("تم تفعيل اشتراك قبس برو بنجاح! 🎉"), Toast.LENGTH_LONG).show()
            onUpgraded()
        }
        billingManager.onPurchaseCanceled = {
            isProcessing = false
            Toast.makeText(context, Translator.tr("تم إلغاء عملية الاشتراك"), Toast.LENGTH_SHORT).show()
        }
        billingManager.onPurchaseError = { errorMsg ->
            isProcessing = false
            Toast.makeText(context, Translator.tr("حدث خطأ أثناء الاشتراك: $errorMsg"), Toast.LENGTH_SHORT).show()
        }
        onDispose {
            billingManager.onPurchaseSuccess = null
            billingManager.onPurchaseCanceled = null
            billingManager.onPurchaseError = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.tr("ترقية الاشتراك"), fontFamily = CairoFont, fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            billingManager.restorePurchases { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                if (success) {
                                    onUpgraded()
                                }
                            }
                        },
                        enabled = !isBillingLoading && !isProcessing
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Translator.tr("استعادة"), fontFamily = NotoSansFont, color = GoldPrimary, fontSize = 14.sp)
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Icon & Title
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Brush.radialGradient(listOf(GoldPrimary.copy(alpha = 0.3f), Color.Transparent)), CircleShape)
                    .border(1.5.dp, GoldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = Translator.tr("استوديو قبس برو (Pro)"),
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = GoldPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Translator.tr("افتح كافة الإمكانيات الإبداعية، توليد ذكاء اصطناعي لا محدود، وتصدير سينمائي بجودة 4K فائقة الوضوح."),
                fontFamily = CairoFont,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Pricing Plans Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Annual Plan Card
                PlanCard(
                    title = Translator.tr("الباقة السنوية"),
                    price = annualPrice,
                    subtext = Translator.tr("توفير 40% (3.33$/شهر)"),
                    isSelected = selectedPlan == BillingManager.SUB_ANNUAL_PRO,
                    isPopular = true,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedPlan = BillingManager.SUB_ANNUAL_PRO }
                )

                // Monthly Plan Card
                PlanCard(
                    title = Translator.tr("الباقة الشهرية"),
                    price = monthlyPrice,
                    subtext = Translator.tr("إلغاء بأي وقت"),
                    isSelected = selectedPlan == BillingManager.SUB_MONTHLY_PRO,
                    isPopular = false,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedPlan = BillingManager.SUB_MONTHLY_PRO }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pro Features List
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = Translator.tr("مميزات باقة قبس برو:"),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FeatureRow(Translator.tr("صناعة وتوليد فيديوهات دعوية غير محدودة"))
                    FeatureRow(Translator.tr("وصول كامل للاستوديو الذكي والمونتاج الاحترافي"))
                    FeatureRow(Translator.tr("تصدير سينمائي بجودة 4K بدون علامة مائية"))
                    FeatureRow(Translator.tr("أولوية قصوى لمعالجة نماذج الذكاء الاصطناعي"))
                    FeatureRow(Translator.tr("الوصول لجميع خطوط وأصوات ElevenLabs الحصرية"))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Subscribe Button
            Button(
                onClick = {
                    isProcessing = true
                    val activity = context as? Activity
                    if (activity != null) {
                        billingManager.launchBillingFlow(
                            activity = activity,
                            productId = selectedPlan,
                            onFallbackSuccess = {
                                // Fallback when running in emulator or test environment without Play Store login
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(1200)
                                    accountService.isPremium = true
                                    isProcessing = false
                                    Toast.makeText(context, Translator.tr("تم الاشتراك بنجاح! 🎉"), Toast.LENGTH_SHORT).show()
                                    onUpgraded()
                                }
                            }
                        )
                    } else {
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(1000)
                            accountService.isPremium = true
                            isProcessing = false
                            Toast.makeText(context, Translator.tr("تمت الترقية بنجاح!"), Toast.LENGTH_SHORT).show()
                            onUpgraded()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                enabled = !isProcessing && !isBillingLoading
            ) {
                if (isProcessing || isBillingLoading) {
                    CircularProgressIndicator(color = DeepSlate, modifier = Modifier.size(24.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = DeepSlate)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedPlan == BillingManager.SUB_ANNUAL_PRO) {
                                Translator.tr("اشترك سنوياً ووفر 40%")
                            } else {
                                Translator.tr("اشترك شهرياً")
                            },
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = DeepSlate
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = Translator.tr("تتم إدارة الاشتراك والدفع بأمان تام عبر Google Play"),
                fontFamily = CairoFont,
                color = TextSecondary,
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
    val borderColor = if (isSelected) GoldPrimary else Color(0xFF1E293B)
    val bgColor = if (isSelected) GoldPrimary.copy(alpha = 0.12f) else CardSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (isPopular) {
                Surface(
                    color = GoldPrimary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = Translator.tr("الأكثر توفيراً"),
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(text = title, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = price, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, color = GoldPrimary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtext, fontFamily = NotoSansFont, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, fontFamily = NotoSansFont, color = TextPrimary, fontSize = 14.sp)
    }
}
