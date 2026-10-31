package com.qabas.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Activity
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GitHub
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.RobotoMonoFont

private val HubBackground = Color(0xFF070B16)
private val HubSurface = Color(0xFF0D1728)
private val HubBorder = Color(0xFF24364D)
private val HubText = Color(0xFFE6EDF7)
private val HubMuted = Color(0xFF8A94A6)
private val HubCyan = Color(0xFF22D3EE)
private val HubGreen = Color(0xFF10B981)
private val HubViolet = Color(0xFF8B5CF6)
private val HubAmber = Color(0xFFF59E0B)

/** Dashboard data is supplied by the real agent engines and repository vault. */
@Composable
fun AgentHubOverview(
    repositoryName: String?,
    repositoryBranch: String?,
    providerName: String,
    modelName: String,
    isOfflineMode: Boolean,
    isProviderReady: Boolean,
    isGitHubReady: Boolean,
    isChecking: Boolean,
    isExecuting: Boolean,
    isChatRunning: Boolean,
    activeMissionTitle: String?,
    activeMissionStatus: String?,
    historyCount: Int,
    onSelectTab: (Int) -> Unit,
    onOpenRepository: () -> Unit,
    onOpenKeys: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(HubBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("مركز التطوير الذكي", color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("مساحة موحدة للمشاريع والمهام ومحركات الذكاء الاصطناعي", color = HubMuted, fontFamily = CairoFont, fontSize = 12.sp)
            }
        }
        item {
            Surface(
                color = HubSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HubCyan.copy(alpha = 0.38f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).background(HubCyan.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Code, contentDescription = null, tint = HubCyan) }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("المشروع النشط", color = HubMuted, fontFamily = CairoFont, fontSize = 11.sp)
                            Text(repositoryName ?: "لم يتم ربط مستودع", color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!repositoryBranch.isNullOrBlank()) Text("branch: $repositoryBranch", color = HubMuted, fontFamily = RobotoMonoFont, fontSize = 11.sp)
                        }
                        Surface(
                            onClick = onOpenRepository,
                            color = HubCyan.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HubCyan.copy(alpha = 0.35f))
                        ) {
                            Text("إدارة", color = HubCyan, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HubStatusPill("GitHub", if (isGitHubReady) "متصل" else "غير متصل", isGitHubReady, Modifier.weight(1f))
                        HubStatusPill(if (isOfflineMode) "النموذج المحلي" else providerName, if (isOfflineMode || isProviderReady) "جاهز" else "يتطلب إعداداً", isOfflineMode || isProviderReady, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Text("حالة بيئة التطوير", color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                HubMetricCard("محرك الذكاء", modelName.ifBlank { "غير محدد" }, if (isOfflineMode || isProviderReady) HubGreen else HubAmber, Icons.Default.Psychology, Modifier.weight(1f))
                HubMetricCard("تنفيذ المهام", when { isExecuting -> "قيد التنفيذ"; isChatRunning -> "محادثة نشطة"; else -> "متوقف" }, if (isExecuting || isChatRunning) HubCyan else HubMuted, Icons.Default.RocketLaunch, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                HubMetricCard("سجل العمليات", historyCount.toString(), HubViolet, Icons.Default.History, Modifier.weight(1f))
                HubMetricCard("فحص الاتصال", if (isChecking) "جارٍ الفحص" else "تحديث الحالة", HubCyan, Icons.Default.Activity, Modifier.weight(1f))
            }
        }
        item {
            Surface(color = HubSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, HubBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = HubViolet, modifier = Modifier.size(18.dp))
                        Text("المهمة النشطة", color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                    Text(activeMissionTitle ?: "لا توجد مهمة نشطة حالياً", color = if (activeMissionTitle == null) HubMuted else HubText, fontFamily = CairoFont, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!activeMissionStatus.isNullOrBlank()) Text("الحالة: $activeMissionStatus", color = HubCyan, fontFamily = CairoFont, fontSize = 11.sp)
                    OutlinedButton(onClick = { onSelectTab(1) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, HubViolet.copy(alpha = 0.55f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = HubText)) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("فتح مساحة العمل", fontFamily = CairoFont, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            Text("الوصول السريع", color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                HubAction("المشاريع", Icons.Default.Folder, HubCyan, { onSelectTab(3) }, Modifier.weight(1f))
                HubAction("النشاط", Icons.Default.History, HubViolet, { onSelectTab(4) }, Modifier.weight(1f))
                HubAction("الأدوات", Icons.Default.Build, HubGreen, { onSelectTab(5) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = onOpenKeys, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, HubBorder)) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("إعدادات الذكاء", fontFamily = CairoFont, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onRefresh, enabled = !isChecking, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, HubBorder)) {
                    Icon(Icons.Default.Activity, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (isChecking) "جارٍ الفحص" else "تحديث الاتصال", fontFamily = CairoFont, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun AgentHubProjectsSection(
    repositories: List<RepoVaultEntry>,
    activeRepositoryId: String?,
    onSelectRepository: (RepoVaultEntry) -> Unit,
    onAddRepository: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(HubBackground).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("المشاريع والمستودعات", color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("إدارة المستودعات المحفوظة وتحديد سياق الوكيل", color = HubMuted, fontFamily = CairoFont, fontSize = 12.sp)
                }
                Surface(onClick = onAddRepository, color = HubCyan.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, HubCyan.copy(alpha = 0.4f))) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = HubCyan, modifier = Modifier.size(16.dp))
                        Text("إضافة", color = HubCyan, fontFamily = CairoFont, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
                    }
                }
            }
        }
        if (repositories.isEmpty()) {
            item {
                Surface(color = HubSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, HubBorder), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = HubMuted, modifier = Modifier.size(30.dp))
                        Text("لا توجد مستودعات محفوظة", color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        Text("أضف مستودع GitHub لربطه بمهام الوكيل.", color = HubMuted, fontFamily = CairoFont, fontSize = 12.sp)
                        Button(onClick = onAddRepository, colors = ButtonDefaults.buttonColors(containerColor = HubCyan, contentColor = HubBackground)) {
                            Text("إضافة مستودع", fontFamily = CairoFont)
                        }
                    }
                }
            }
        } else {
            items(repositories, key = { it.id }) { repository ->
                val selected = repository.id == activeRepositoryId
                Surface(
                    onClick = { onSelectRepository(repository) },
                    color = HubSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (selected) HubGreen.copy(alpha = 0.7f) else HubBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GitHub, contentDescription = null, tint = if (selected) HubGreen else HubCyan, modifier = Modifier.size(18.dp))
                            Text(repository.displayName, color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (selected) HubStatusPill("السياق النشط", "محدد", true)
                        }
                        Text(repository.fullName, color = HubMuted, fontFamily = RobotoMonoFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الفرع: ${repository.defaultBranch.ifBlank { "main" }}", color = HubMuted, fontFamily = CairoFont, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text(if (repository.isPrivate) "خاص" else "عام", color = HubMuted, fontFamily = CairoFont, fontSize = 11.sp)
                        }
                        if (repository.description.isNotBlank()) Text(repository.description, color = HubMuted, fontFamily = CairoFont, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun HubStatusPill(label: String, value: String, ready: Boolean, modifier: Modifier = Modifier) {
    Surface(color = (if (ready) HubGreen else HubAmber).copy(alpha = 0.10f), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, (if (ready) HubGreen else HubAmber).copy(alpha = 0.35f)), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(label, color = HubMuted, fontFamily = CairoFont, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = if (ready) HubGreen else HubAmber, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HubMetricCard(label: String, value: String, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(color = HubSurface, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, HubBorder), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(label, color = HubMuted, fontFamily = CairoFont, fontSize = 11.sp)
            Text(value, color = HubText, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HubAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = HubSurface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)), modifier = modifier.height(54.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(label, color = HubText, fontFamily = CairoFont, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
