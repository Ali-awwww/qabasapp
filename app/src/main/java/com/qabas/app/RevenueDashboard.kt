package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * لوحة الإيرادات المتقدمة:
 * - مجاميع حقيقية من TransactionManager وسحابة Supabase.
 * - رسم بياني شهري (آخر 6 أشهر) بأعمدة Canvas.
 * - تقسيم حسب المنتج + إجمالي + تصدير CSV.
 * - أدوات تجربة ومسح المعاملات للمطورين.
 */
@Composable
fun RevenueDashboard(context: Context) {
    LaunchedEffect(Unit) {
        TransactionManager.init(context)
    }

    val purchases by CloudServices.Database.observeAllTransactions().collectAsState(initial = emptyList())
    val scrollState = rememberScrollState()

    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(buildRevenueCsv(purchases).toByteArray()) }
            Toast.makeText(context, "تم تصدير الإيرادات بصيغة CSV بنجاح ✅", Toast.LENGTH_SHORT).show()
            AuditLogger.log(context, "revenue_export_csv", "تصدير ${purchases.size} معاملة")
        }
    }

    val total = purchases.sumOf { (it["priceAmount"] as? Double) ?: 0.0 }
    val byProduct = productTotals(purchases)

    // آخر 6 أشهر
    val monthly = monthlyTotals(purchases)

    // مؤشرات الاشتراكات (RevenueCat-style) — محسوبة من منتجات pro_* الحقيقية
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val mrr = purchases.sumOf {
        val pid = it["productId"] as? String ?: return@sumOf 0.0
        val ts = (it["timestamp"] as? Long) ?: 0L
        when {
            pid == "pro_1m" && now - ts < 30 * dayMs -> 9.99
            pid == "pro_1y" && now - ts < 365 * dayMs -> 89.99 / 12.0
            else -> 0.0
        }
    }
    val activeSubs = purchases.count {
        val pid = it["productId"] as? String ?: return@count false
        val ts = (it["timestamp"] as? Long) ?: 0L
        (pid == "pro_1m" && now - ts < 30 * dayMs) || (pid == "pro_1y" && now - ts < 365 * dayMs)
    }
    val last30 = purchases.filter { ((it["timestamp"] as? Long) ?: 0L) >= now - 30 * dayMs }
        .sumOf { (it["priceAmount"] as? Double) ?: 0.0 }
    val prev30 = purchases.filter {
        val ts = (it["timestamp"] as? Long) ?: 0L
        ts >= now - 60 * dayMs && ts < now - 30 * dayMs
    }.sumOf { (it["priceAmount"] as? Double) ?: 0.0 }
    val growthPct = if (prev30 > 0) (last30 - prev30) / prev30 * 100.0 else if (last30 > 0) 100.0 else 0.0

    // معدل إعادة الشراء: مشترون بأكثر من عملية / إجمالي المشترين
    val byUser = purchases.groupBy { it["userId"] as? String ?: "?" }
    val repeatRate = if (byUser.isNotEmpty())
        byUser.count { it.value.size > 1 } * 100.0 / byUser.size else 0.0

    fun proExpiry(pid: String, ts: Long): Long = when (pid) {
        "pro_1m" -> ts + 30 * dayMs
        "pro_1y" -> ts + 365 * dayMs
        else -> 0L
    }
    val proBuyers = byUser.filter { (_, list) -> list.any { (it["productId"] as? String ?: "").startsWith("pro_") } }
    val churned = proBuyers.count { (_, list) ->
        val latestExpiry = list
            .mapNotNull {
                val pid = it["productId"] as? String ?: return@mapNotNull null
                if (!pid.startsWith("pro_")) return@mapNotNull null
                proExpiry(pid, (it["timestamp"] as? Long) ?: 0L)
            }.maxOrNull() ?: 0L
        latestExpiry in 1 until now
    }
    val churnRate = if (proBuyers.isNotEmpty()) churned * 100.0 / proBuyers.size else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
        ) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("إجمالي المبيعات (حقيقي)", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp)
                    Text("$${"%.2f".format(total)}", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Text("${purchases.size} معاملة", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                }
                TextButton(
                    onClick = {
                        if (purchases.isEmpty()) {
                            Toast.makeText(context, "لا توجد معاملات بعد للتصدير", Toast.LENGTH_SHORT).show()
                        } else {
                            csvExport.launch("qabas_revenue_${System.currentTimeMillis()}.csv")
                        }
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تصدير CSV", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (purchases.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(GoldPrimary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(32.dp))
                    }

                    Text("بوابة المبيعات والإيرادات جاهزة ومربوطة", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "لم يتم تسجيل أي مبيعات حقيقية بعد. يمكنك تسجيل معاملة تجريبية لمعاينة الرسوم البيانية ومؤشرات الـ MRR أو فحص المزامنة مع السحابة.",
                        color = TextSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                AppSoundManager.playSuccess()
                                TransactionManager.recordPurchase(
                                    context = context,
                                    productId = "pro_1m",
                                    priceAmount = 9.99
                                )
                                Toast.makeText(context, "تمت إضافة معاملة تجريبية (اشتراك شهري $9.99) ✦", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("🧪 تجربة شهري ($9.99)", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                AppSoundManager.playSuccess()
                                TransactionManager.recordPurchase(
                                    context = context,
                                    productId = "pro_1y",
                                    priceAmount = 89.99
                                )
                                Toast.makeText(context, "تمت إضافة معاملة تجريبية (اشتراك سنوي $89.99) ✦", Toast.LENGTH_SHORT).show()
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("🧪 تجربة سنوي ($89.99)", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            // مؤشرات الأداء: MRR + النمو + الاشتراكات النشطة + إعادة الشراء
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RevenueKpi(
                    value = "$${"%.2f".format(mrr)}", label = "الإيراد الشهري MRR",
                    tint = Color(0xFF10B981), modifier = Modifier.weight(1f)
                )
                RevenueKpi(
                    value = "${if (growthPct >= 0) "+" else ""}${"%.0f".format(growthPct)}٪",
                    label = "نمو 30 يوم",
                    tint = if (growthPct >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RevenueKpi(
                    value = activeSubs.toString(), label = "اشتراكات نشطة",
                    tint = Color(0xFF38BDF8), modifier = Modifier.weight(1f)
                )
                RevenueKpi(
                    value = "${"%.0f".format(repeatRate)}٪", label = "إعادة الشراء",
                    tint = GoldPrimary, modifier = Modifier.weight(1f)
                )
                RevenueKpi(
                    value = "${"%.0f".format(churnRate)}٪", label = "التسرب",
                    tint = if (churnRate > 30) Color(0xFFEF4444) else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // الرسم البياني الشهري
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("المبيعات الشهرية (آخر 6 أشهر)", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    val maxVal = monthly.maxOfOrNull { it.value } ?: 0.0
                    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        if (monthly.isEmpty()) return@Canvas
                        val barWidth = size.width / monthly.size * 0.6f
                        val gap = size.width / monthly.size
                        monthly.forEachIndexed { i, m ->
                            val h = if (maxVal > 0) (m.value / maxVal * size.height * 0.85f).toFloat() else 0f
                            val left = i * gap + gap * 0.2f
                            drawRoundRect(
                                color = if (m.value > 0) GoldPrimary else Color(0xFF1E293B),
                                topLeft = Offset(left, size.height - h),
                                size = Size(barWidth, h),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        monthly.forEach { m ->
                            Text(m.label, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }

            // تقسيم حسب المنتج
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("التقسيم حسب المنتج", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    byProduct.forEach { (product, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(product, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                            Text("$${"%.2f".format(value)}", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // قائمة المعاملات
            Text("أحدث المعاملات (${purchases.size})", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            purchases.take(25).forEach { purchase ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(purchase["productId"] as? String ?: "غير معروف", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val ts = purchase["timestamp"] as? Long ?: 0L
                            Text(
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts)),
                                color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp
                            )
                        }
                        Text("$${purchase["priceAmount"]}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // أدوات إدارة الاختبارات للمطور (إضافة أو تنظيف البيانات التجريبية)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        AppSoundManager.playSuccess()
                        TransactionManager.recordPurchase(
                            context = context,
                            productId = "pro_1m",
                            priceAmount = 9.99
                        )
                        Toast.makeText(context, "تمت إضافة معاملة تجريبية ✦", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة معاملة تجريبية", color = GoldPrimary, fontFamily = CairoFont, fontSize = 11.5.sp)
                }

                TextButton(
                    onClick = {
                        AppSoundManager.playToggle()
                        TransactionManager.clearAllTransactions(context)
                        Toast.makeText(context, "تم مسح البيانات التجريبية بنجاح 🗑️", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مسح المعاملات التجريبية", color = Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 11.5.sp)
                }
            }
        }
    }
}

private data class MonthTotal(val label: String, val value: Double)

@Composable
private fun RevenueKpi(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = tint, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(label, color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
        }
    }
}

private fun monthlyTotals(purchases: List<Map<String, Any>>): List<MonthTotal> {
    val result = mutableListOf<MonthTotal>()
    val fmt = SimpleDateFormat("MMM", Locale.getDefault())
    for (i in 5 downTo 0) {
        val start = Calendar.getInstance().apply {
            add(Calendar.MONTH, -i)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            add(Calendar.MONTH, 1)
        }
        val sum = purchases.filter {
            val ts = (it["timestamp"] as? Long) ?: 0L
            ts >= start.timeInMillis && ts < end.timeInMillis
        }.sumOf { (it["priceAmount"] as? Double) ?: 0.0 }
        result.add(MonthTotal(fmt.format(start.time), sum))
    }
    return result
}

private fun buildRevenueCsv(purchases: List<Map<String, Any>>): String {
    val sb = StringBuilder()
    sb.append("المنتج,المعرف,التاريخ,المبلغ,العملة\n")
    purchases.forEach { p ->
        val ts = p["timestamp"] as? Long ?: 0L
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
        sb.append("\"${p["productId"]}\",\"${p["id"] ?: p["purchaseToken"] ?: ""}\",\"$date\",${p["priceAmount"]},\"${p["currency"] ?: "USD"}\"\n")
    }
    return sb.toString()
}

private fun productTotals(purchases: List<Map<String, Any>>): Map<String, Double> {
    val map = linkedMapOf<String, Double>()
    purchases.forEach { p ->
        val key = p["productId"] as? String ?: "غير معروف"
        map[key] = (map[key] ?: 0.0) + ((p["priceAmount"] as? Double) ?: 0.0)
    }
    return map
}
