package com.qabas.app

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
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
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onPaymentSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paymentService = remember { AppServices.getPaymentService(context) }
    val accountService = remember { AppServices.getAccountService(context) }
    
    val isProOrDev = accountService.isPremium || accountService.isDeveloperOrAdmin || accountService.hasCustomKeys
    val videoCost = if (isProOrDev) 0 else 5
    
    var currentBalance by remember { mutableIntStateOf(paymentService.userBalance) }
    var isProcessing by remember { mutableStateOf(false) }
    var paymentSuccess by remember { mutableStateOf(false) }
    var showRechargeDialog by remember { mutableStateOf(false) }

    val goldGradient = Brush.horizontalGradient(colors = listOf(GoldSecondary, GoldPrimary))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        Translator.tr("الدفع والتصدير"), 
                        fontFamily = CairoFont, 
                        fontWeight = FontWeight.Bold, 
                        color = GoldPrimary,
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Translator.tr("العودة"), tint = GoldPrimary)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Receipt Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isProOrDev) GoldPrimary.copy(alpha = 0.5f) else Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isProOrDev) Icons.Default.WorkspacePremium else Icons.Default.Diamond,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isProOrDev) Translator.tr("تصدير فيديو بدقة عالية") else Translator.tr("تكلفة تصدير الفيديو"),
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontFamily = CairoFont
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isProOrDev) "0" else "$videoCost",
                            color = GoldPrimary,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoMonoFont
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Diamond, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        text = if (isProOrDev) Translator.tr("ميزة مجانية لمشتركي قبس برو 👑") else Translator.tr("عملات قبس"),
                        color = if (isProOrDev) Color(0xFF22C55E) else GoldPrimary,
                        fontSize = 14.sp,
                        fontFamily = CairoFont,
                        fontWeight = if (isProOrDev) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // User Balance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = Translator.tr("رصيدك الحالي"),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$currentBalance",
                        color = if (isProOrDev || currentBalance >= videoCost) Color(0xFF22C55E) else Color(0xFFEF4444),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RobotoMonoFont
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Diamond, 
                        contentDescription = null, 
                        tint = if (isProOrDev || currentBalance >= videoCost) Color(0xFF22C55E) else Color(0xFFEF4444), 
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isProOrDev && currentBalance < videoCost) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = Translator.tr("رصيدك الحالي غير كافٍ لتصدير الفيديو. يرجى شحن الرصيد للمتابعة."),
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(16.dp),
                        fontFamily = CairoFont,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { showRechargeDialog = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Translator.tr("شحن الرصيد الآن 💰"), color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            val success = if (isProOrDev) true else paymentService.processPayment(videoCost)
                            if (success) {
                                currentBalance = paymentService.userBalance
                                paymentSuccess = true
                                paymentService.generateInvoice(context, "PRJ-${System.currentTimeMillis()}", videoCost)
                                
                                // Record transaction in cloud
                                if (!isProOrDev) {
                                    CloudServices.Database.recordPurchase("video_export_hd", "coin_spent_${System.currentTimeMillis()}")
                                }
                                
                                delay(1200)
                                onPaymentSuccess()
                            } else {
                                isProcessing = false
                                android.widget.Toast.makeText(context, Translator.tr("فشلت عملية الدفع!"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isProcessing
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().then(if (isProcessing) Modifier.background(CardSurface) else Modifier.background(goldGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            if (paymentSuccess) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isProOrDev) Translator.tr("تم التجهيز للتصدير! 🚀") else Translator.tr("تم الدفع بنجاح! 🎉"), 
                                        color = Color(0xFF22C55E), 
                                        fontFamily = CairoFont, 
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(24.dp))
                            }
                        } else {
                            Text(
                                text = if (isProOrDev) Translator.tr("تصدير الفيديو الآن (مجاناً لمشتركي برو 👑)") else Translator.tr("تأكيد الدفع والتصدير (خصم $videoCost عملات)"), 
                                color = DeepSlate, 
                                fontFamily = TajawalFont, 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRechargeDialog) {
        AlertDialog(
            onDismissRequest = { showRechargeDialog = false },
            title = { Text(Translator.tr("شحن رصيد قبس السريع 💰"), fontFamily = CairoFont, color = GoldPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RechargeOption(amount = 50, price = "$4.99", onClick = {
                        scope.launch {
                            paymentService.recharge(50)
                            currentBalance = paymentService.userBalance
                            CloudServices.Database.recordPurchase("coin_50", "token_${System.currentTimeMillis()}")
                            showRechargeDialog = false
                            android.widget.Toast.makeText(context, Translator.tr("تم شحن الرصيد بنجاح (+50 عملة)! 🎉"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                    RechargeOption(amount = 100, price = "$8.99", onClick = {
                        scope.launch {
                            paymentService.recharge(100)
                            currentBalance = paymentService.userBalance
                            CloudServices.Database.recordPurchase("coin_100", "token_${System.currentTimeMillis()}")
                            showRechargeDialog = false
                            android.widget.Toast.makeText(context, Translator.tr("تم شحن الرصيد بنجاح (+100 عملة)! 🎉"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                    RechargeOption(amount = 500, price = "$39.99", isPopular = true, onClick = {
                        scope.launch {
                            paymentService.recharge(500)
                            currentBalance = paymentService.userBalance
                            CloudServices.Database.recordPurchase("coin_500", "token_${System.currentTimeMillis()}")
                            showRechargeDialog = false
                            android.widget.Toast.makeText(context, Translator.tr("تم شحن الرصيد بنجاح (+500 عملة)! 🎉"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            },
            confirmButton = {
                TextButton(onClick = { showRechargeDialog = false }) {
                    Text(Translator.tr("إلغاء"), color = TextSecondary, fontFamily = CairoFont)
                }
            },
            containerColor = CardSurface
        )
    }
}

@Composable
fun RechargeOption(amount: Int, price: String, isPopular: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DeepSlate),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isPopular) GoldPrimary else Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Diamond, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("$amount ${Translator.tr("عملة")}", color = TextPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (isPopular) {
                    Text(Translator.tr("الأكثر طلبًا"), color = GoldPrimary, fontSize = 12.sp, fontFamily = NotoSansFont)
                }
                Text(price, color = TextSecondary, fontFamily = RobotoMonoFont, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
