package com.qabas.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.components.AppleCard
import com.qabas.app.ui.components.AppleInsetDivider
import com.qabas.app.ui.components.AppleListRow
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.NotoSansFont
import com.qabas.app.ui.theme.QabasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CloudIntegrationMonitorPanel — لوحة المراقبة الحية لـ Supabase المعتمد كسحابة حصرية وحالة الخدمات الخارجية.
 */
@Composable
fun CloudIntegrationMonitorPanel(
    modifier: Modifier = Modifier,
    showArchitectureOverview: Boolean = true
) {
    val context = LocalContext.current
    val theme = QabasTheme.current
    val coroutineScope = rememberCoroutineScope()

    var isChecking by remember { mutableStateOf(false) }
    var supabaseConfigured by remember { mutableStateOf(false) }
    var supabaseConnected by remember { mutableStateOf<Boolean?>(null) }
    var supabaseLatency by remember { mutableLongStateOf(0L) }
    var supabaseDetail by remember { mutableStateOf("جارٍ فحص الاتصال...") }
    var serviceStatuses by remember { mutableStateOf(ServiceAvailabilityRegistry.getAllServiceStatuses(context)) }

    fun runLiveDiagnostics() {
        coroutineScope.launch {
            isChecking = true
            supabaseConfigured = SupabaseServices.isSupabaseAvailable
            val sbStart = System.currentTimeMillis()
            val sbOk = withContext(Dispatchers.IO) {
                SupabaseServices.Database.ping()
            }
            supabaseLatency = System.currentTimeMillis() - sbStart
            // ق94: الاتصال يعني نجاح الاختبار الحي فقط — كان «مهيأ»
            // يكفي ليُعرض «حصري ومعتمد» و«نشط» رغم فشل الاختبار.
            supabaseConnected = sbOk
            supabaseDetail = when {
                sbOk -> "سحابة PostgreSQL متصلة بنجاح وتستجيب للبيانات."
                supabaseConfigured -> "المفاتيح مهيأة محلياً؛ تعذر اختبار الاستعلام حالياً."
                else -> "Supabase غير مفعّل — يرجى ضبط SUPABASE_URL و SUPABASE_ANON_KEY."
            }
            serviceStatuses = ServiceAvailabilityRegistry.getAllServiceStatuses(context)
            isChecking = false
        }
    }

    LaunchedEffect(Unit) {
        runLiveDiagnostics()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showArchitectureOverview) {
            AppleCard(cornerRadius = 16.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "البنية السحابية المعتمدة (Supabase)",
                                color = theme.textPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Surface(
                            color = (if (supabaseConnected == true) theme.success else theme.warning).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                // ق94: ثلاث حالات صادقة — متصل / يفحص /
                                // مهيأ بلا اتصال مؤكد / غير مهيأ. كان
                                // الفشل يظهر «يحتاج إعداد» رغم التهيئة.
                                text = when {
                                    supabaseConnected == true -> "حصري ومعتمد"
                                    isChecking -> "جارٍ الفحص..."
                                    supabaseConfigured -> "مهيأ — تعذر الاتصال"
                                    else -> "يحتاج إعداد"
                                },
                                color = if (supabaseConnected == true) theme.success else theme.warning,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "يعتمد التطبيق على Supabase حصرياً للمصادقة، قاعدة البيانات PostgreSQL، الريلز، التخزين السحابي، وخزينة المفاتيح.",
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "فحص الاتصال الحي وجاهزية الخدمات",
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            TextButton(
                onClick = { runLiveDiagnostics() },
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = theme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("جارٍ الفحص...", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تحديث الفحص", color = theme.primary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        CloudServiceStatusCard(
            serviceName = "Supabase Relational Cloud",
            providerTag = "PostgreSQL · Auth · Storage · Realtime",
            icon = Icons.Default.Storage,
            accentColor = theme.success,
            isActive = supabaseConnected == true,
            checking = isChecking,
            latencyMs = supabaseLatency,
            detail = supabaseDetail,
            metrics = listOf(
                "قاعدة البيانات" to if (supabaseConnected == true) "نشط" else "غير متاح",
                "المصادقة (RLS)" to if (supabaseConfigured) "مهيأ" else "غير متاح",
                "الحساب الحالي" to if (ServiceAvailabilityRegistry.isUserSignedIn()) "مسجّل" else "ضيف",
                "الاستجابة" to if (supabaseLatency > 0) "${supabaseLatency}ms" else "--"
            )
        )

        AppleCard(cornerRadius = 16.dp) {
            serviceStatuses.forEachIndexed { index, status ->
                AppleListRow(
                    title = status.displayName,
                    subtitle = if (status.isAvailable) null else status.missingReason,
                    trailingText = status.modeLabel,
                    showChevron = false
                )
                if (index < serviceStatuses.lastIndex) {
                    AppleInsetDivider()
                }
            }
        }
    }
}

@Composable
private fun CloudServiceStatusCard(
    serviceName: String,
    providerTag: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    checking: Boolean,
    latencyMs: Long,
    detail: String,
    metrics: List<Pair<String, String>>
) {
    val theme = QabasTheme.current
    val statusColor = if (isActive) accentColor else theme.destructive

    AppleCard(cornerRadius = 16.dp) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(serviceName, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(providerTag, color = theme.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            checking -> "جارٍ الفحص..."
                            isActive -> if (latencyMs > 0) "نشط (${latencyMs}ms)" else "نشط"
                            else -> "غير نشط"
                        },
                        color = statusColor,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                color = theme.secondarySurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (checking) "جارٍ فحص الاتصال وزمن الاستجابة..." else detail,
                        color = theme.textSecondary,
                        fontFamily = NotoSansFont,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        metrics.forEach { (key, value) ->
                            Column {
                                Text(key, color = theme.textSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
                                Text(value, color = theme.textPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
