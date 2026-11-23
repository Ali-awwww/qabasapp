package com.qabas.app

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
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
private val HubAmber = Color(0xFFF59E0B)

@Composable
fun AgentHubProjectsSection(
    repositories: List<RepoVaultEntry>,
    activeRepositoryId: String?,
    onSelectRepository: (RepoVaultEntry) -> Unit,
    onAddRepository: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(HubBackground).padding(horizontal = 16.dp).testTag("agent_hub_projects"),
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
                            Icon(Icons.Default.Code, contentDescription = null, tint = if (selected) HubGreen else HubCyan, modifier = Modifier.size(18.dp))
                            Text(repository.displayName, color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (selected) HubStatusPill("السياق النشط", "محدد", true)
                        }
                        Text(repository.fullName, color = HubMuted, fontFamily = RobotoMonoFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الفرع: ${repository.defaultBranch.ifBlank { "غير محدد" }}", color = HubMuted, fontFamily = CairoFont, fontSize = 11.sp, modifier = Modifier.weight(1f))
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

/** Shared, compact page identity for the developer hub's functional sections. */
@Composable
internal fun AgentHubPageIntro(
    index: String,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HubSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("agent_hub_page_$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(HubCyan.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = HubCyan, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(index, color = HubCyan, fontFamily = RobotoMonoFont, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text(title, color = HubText, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(description, color = HubMuted, fontFamily = CairoFont, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
