package com.qabas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.qabas.app.ui.theme.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// نظرة عامة على النمو للوحة المطور (الدفعة 5 — الجزء أ، إعادة إنشاء)
// ============================================================================
//
// 6 بطاقات مؤشرات من بيانات حقيقية فقط (AnalyticsOverviewRepository).
// أي بطاقة بلا مصدر بيانات تعرض «لا توجد بيانات بعد» ولا تعرض صفراً
// على أنه قياس. لا بطاقة «معدل خالٍ من الأعطال»: لا مصدر لإجمالي
// الجلسات، فيبقى «لا بيانات» موثقاً لا بطاقة (قاعدة الدفعة 5-ب).

private const val NO_DATA = "لا توجد بيانات بعد"

@Composable
fun DashboardOverviewScreen(onBack: () -> Unit = {}) {
    var stats by remember { mutableStateOf<OverviewStats?>(null) }

    LaunchedEffect(Unit) {
        stats = AnalyticsOverviewRepository.loadOverview()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع", color = PresetPrimary) }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "نظرة عامة — النمو",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val loaded = stats
        if (loaded == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PresetPrimary)
            }
            return@Column
        }

        if (!loaded.analyticsAvailable) {
            OverviewNoticeCard(
                title = "جدول أحداث التحليلات غير مطبَّق بعد",
                body = "طبّق ترحيل analytics_events من محرر SQL في لوحة Supabase " +
                    "ليبدأ تسجيل الأحداث وظهور مقاييس النشاط. " +
                    "إجمالي المستخدمين أدناه يُقرأ من profiles وهو حقيقي."
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ---- صف المؤشرات: بطاقتان في كل صف ----
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewKpiCard(
                title = "المستخدمون النشطون اليوم (DAU)",
                value = loaded.dau?.toString(),
                subtitle = loaded.anonSessionsToday?.let { "جلسات مجهولة اليوم: $it" },
                icon = Icons.Default.TrendingUp,
                accent = PresetPrimary,
                modifier = Modifier.weight(1f)
            )
            OverviewKpiCard(
                title = "المستخدمون النشطون شهرياً (MAU)",
                value = loaded.mau?.toString(),
                subtitle = "مميزون في آخر 30 يوماً",
                icon = Icons.Default.Group,
                accent = ThemeSuccess,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewKpiCard(
                title = "مستخدمون جدد (7 أيام)",
                value = loaded.newUsers7d?.toString(),
                subtitle = "من تاريخ إنشاء الملف",
                icon = Icons.Default.PersonAdd,
                accent = PresetAccent,
                modifier = Modifier.weight(1f)
            )
            OverviewKpiCard(
                title = "إجمالي المستخدمين",
                value = loaded.totalUsers?.toString(),
                subtitle = "صفوف profiles",
                icon = Icons.Default.Group,
                accent = ThemeWarning,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewKpiCard(
                title = "أحداث اليوم",
                value = loaded.eventsToday?.toString(),
                subtitle = "صفوف analytics_events اليوم",
                icon = Icons.Default.Event,
                accent = ThemeDestructive,
                modifier = Modifier.weight(1f)
            )
            OverviewKpiCard(
                title = "أكثر الأحداث (7 أيام)",
                value = loaded.topEvents.firstOrNull()?.let { "${it.eventName} (${it.count})" },
                subtitle = if (loaded.topEvents.isEmpty()) null else "الأول من ${loaded.topEvents.size}",
                icon = Icons.Default.BarChart,
                accent = PresetPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ---- عمود DAU لآخر 7 أيام ----
        OverviewSectionCard(title = "المستخدمون النشطون — آخر 7 أيام") {
            if (!loaded.analyticsAvailable || loaded.dailyDau.isEmpty()) {
                Text(NO_DATA, color = TextSecondary, fontSize = 14.sp)
            } else {
                val max = loaded.dailyDau.maxOf { it.count }.coerceAtLeast(1)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    loaded.dailyDau.forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.count.toString(),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height((8 + day.count * 72 / max).dp)
                                    .background(PresetPrimary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(day.dayLabel, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- أكثر الأحداث ----
        OverviewSectionCard(title = "أكثر الأحداث — آخر 7 أيام") {
            if (loaded.topEvents.isEmpty()) {
                Text(NO_DATA, color = TextSecondary, fontSize = 14.sp)
            } else {
                loaded.topEvents.forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(event.eventName, color = TextPrimary, fontSize = 14.sp)
                        Text(event.count.toString(), color = PresetPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ملاحظة: معدل خالٍ من الأعطال غير معروض — لا مصدر لإجمالي الجلسات حتى يُقاس عليه.",
            color = TextTertiary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun OverviewKpiCard(
    title: String,
    value: String?,
    subtitle: String?,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DeepSlate)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value ?: NO_DATA,
                color = if (value == null) TextSecondary else TextPrimary,
                fontSize = if (value == null) 14.sp else 26.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = TextTertiary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun OverviewSectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DeepSlate)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun OverviewNoticeCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ThemeWarning.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = ThemeWarning, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(body, color = TextPrimary, fontSize = 13.sp)
        }
    }
}
