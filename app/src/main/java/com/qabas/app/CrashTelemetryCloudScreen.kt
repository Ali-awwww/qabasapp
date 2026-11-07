package com.qabas.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val crashReportDateFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

private fun crashReportValue(description: String, key: String): String? =
    description.lineSequence()
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun crashReportTimestamp(report: SupabaseServices.BugReportRow): Long {
    val raw = report.createdAt
        ?.takeIf { it.isNotBlank() }
        ?: crashReportValue(report.description, "occurred_at")
        ?: return 0L
    return runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
}

private fun crashReportDate(report: SupabaseServices.BugReportRow): String {
    val timestamp = crashReportTimestamp(report)
    return if (timestamp <= 0L) "وقت غير متاح"
    else crashReportDateFormatter.format(Instant.ofEpochMilli(timestamp))
}

/**
 * Cloud-only console for reports emitted automatically by CrashTelemetry.
 * It intentionally does not read local crash files or user-submitted camera reports.
 */
@Composable
fun CrashTelemetryCloudScreen() {
    val authUser by SupabaseServices.Auth.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val canView = remember(authUser) { AdminGuard.isCrashTelemetryAccessAllowed() }

    var reports by remember { mutableStateOf<List<SupabaseServices.BugReportRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedReport by remember { mutableStateOf<SupabaseServices.BugReportRow?>(null) }

    fun refresh() {
        if (!canView || isLoading) return
        isLoading = true
        loadError = null
        scope.launch {
            try {
                reports = SupabaseServices.Database.getCrashTelemetryReports()
            } catch (error: Exception) {
                loadError = error.message ?: "تعذر تحميل تقارير الأعطال من السحابة"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(canView) {
        if (canView) refresh()
    }

    if (!canView) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null)
                Text("هذا القسم مخصص للمطورين المعتمدين")
            }
        }
        return
    }

    val now = System.currentTimeMillis()
    val last24Hours = reports.count {
        val timestamp = crashReportTimestamp(it)
        timestamp in (now - 24L * 60 * 60 * 1000)..now
    }
    val last7Days = reports.count {
        val timestamp = crashReportTimestamp(it)
        timestamp in (now - 7L * 24 * 60 * 60 * 1000)..now
    }
    val issueGroups = reports.groupBy {
        crashReportValue(it.description, "exception_type") ?: "نوع غير محدد"
    }.entries.sortedByDescending { it.value.size }
    val filteredReports = remember(reports, searchQuery) {
        if (searchQuery.isBlank()) reports
        else reports.filter {
            it.description.contains(searchQuery, ignoreCase = true) ||
                it.deviceInfo.contains(searchQuery, ignoreCase = true) ||
                it.appVersion.contains(searchQuery, ignoreCase = true) ||
                it.screenName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("تقارير الأعطال التلقائية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "تقارير وصلت إلى Supabase من أجهزة المستخدمين — دون بلاغ يدوي",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { refresh() }, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث التقارير")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CrashTelemetryMetricCard("المعروض", reports.size.toString(), Modifier.weight(1f))
            CrashTelemetryMetricCard("آخر 24 ساعة", last24Hours.toString(), Modifier.weight(1f))
            CrashTelemetryMetricCard("آخر 7 أيام", last7Days.toString(), Modifier.weight(1f))
        }

        if (issueGroups.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("أكثر أنواع الاستثناءات تكراراً", fontWeight = FontWeight.SemiBold)
                    issueGroups.take(4).forEach { (type, items) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(type, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(items.size.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("ابحث في التقارير") }
        )

        when {
            isLoading && reports.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }
            }
            loadError != null && reports.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { refresh() }) { Text("إعادة المحاولة") }
                }
            }
            filteredReports.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Text(
                        if (reports.isEmpty()) "لا توجد تقارير أعطال تلقائية مستلمة حتى الآن"
                        else "لا توجد تقارير تطابق البحث"
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredReports, key = { it.id }) { report ->
                        val exceptionType = crashReportValue(report.description, "exception_type") ?: "استثناء غير محدد"
                        val source = crashReportValue(report.description, "source") ?: "مصدر غير محدد"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selectedReport = report },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(exceptionType, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(crashReportDate(report), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    crashReportValue(report.description, "message") ?: "لا توجد رسالة استثناء",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "$source • ${report.appVersion} • ${report.deviceInfo}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedReport = null },
            title = { Text(crashReportValue(report.description, "exception_type") ?: "تفاصيل العطل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("وقت الاستلام: ${report.createdAt ?: "غير متاح"}")
                    Text("الجهاز: ${report.deviceInfo}")
                    Text("إصدار قبس: ${report.appVersion}")
                    Text("الشاشة: ${report.screenName}")
                    Text("المصدر: ${crashReportValue(report.description, "source") ?: "غير محدد"}")
                    Text(
                        report.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.height(300.dp).verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedReport = null }) { Text("إغلاق") }
            }
        )
    }
}

@Composable
private fun CrashTelemetryMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
