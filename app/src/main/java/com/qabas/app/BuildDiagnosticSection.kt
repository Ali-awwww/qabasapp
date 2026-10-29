package com.qabas.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.agent.AgentDesign
import com.qabas.app.ui.agent.AgentNeonCard
import com.qabas.app.ui.agent.AgentSectionHeader
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun BuildDiagnosticSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(false) }
    var githubInfo by remember { mutableStateOf<UpdateManager.GitHubReleaseInspection?>(null) }
    var supabaseInfo by remember { mutableStateOf<ActiveUpdate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val currentVersionCode = UpdateManager.getCurrentVersionCode(context)
    val currentVersionName = UpdateManager.getCurrentVersionName(context)

    val refreshData = {
        scope.launch {
            isLoading = true
            error = null
            try {
                githubInfo = UpdateManager.inspectGitHubRelease(context).getOrNull()
                supabaseInfo = SupabaseServices.Database.getLatestActiveUpdate()
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AgentSectionHeader(index = "01", title = "معلومات البناء الحالية", accent = GoldPrimary)
            Spacer(Modifier.height(8.dp))
            BuildInfoCard(
                title = "النسخة المحلية",
                versionName = currentVersionName,
                versionCode = currentVersionCode,
                status = "نشط حالياً",
                statusColor = GoldPrimary
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AgentSectionHeader(index = "02", title = "مقارنة الإصدارات السحابية", accent = Color(0xFF0EA5E9))
                IconButton(onClick = { refreshData() }, enabled = !isLoading) {
                    Icon(
                        Icons.Default.Refresh, 
                        contentDescription = "تحديث", 
                        tint = if (isLoading) Color.Gray else Color(0xFF0EA5E9)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading && githubInfo == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldPrimary)
                }
            }
        } else {
            item {
                githubInfo?.let { info ->
                    val isMatch = info.tagName.contains(currentVersionName)
                    BuildInfoCard(
                        title = "GitHub (Latest Release)",
                        versionName = info.tagName,
                        versionCode = UpdateManager.parseReleaseVersionCode(info.tagName),
                        status = if (info.isNewerThanCurrent) "يوجد تحديث أحدث 🚀" else if (isMatch) "مطابق للنسخة الحالية ✅" else "نسخة قديمة أو مختلفة ⚠️",
                        statusColor = if (info.isNewerThanCurrent) Color(0xFFF59E0B) else if (isMatch) Color(0xFF10B981) else Color.Red,
                        detail = "نُشر في: ${info.publishedAt}"
                    )
                } ?: Text("تعذر جلب بيانات GitHub", color = Color.Red, fontSize = 12.sp)
            }

            item {
                supabaseInfo?.let { info ->
                    val isMatch = info.versionName == currentVersionName && info.versionCode == currentVersionCode
                    val isNewer = info.versionCode > currentVersionCode
                    BuildInfoCard(
                        title = "Supabase (Active Update)",
                        versionName = info.versionName,
                        versionCode = info.versionCode,
                        status = if (isNewer) "تحديث متوفر في المتجر 📥" else if (isMatch) "مطابقة تماماً ✅" else "نسخة سحابية أقدم 🔙",
                        statusColor = if (isNewer) Color(0xFFF59E0B) else if (isMatch) Color(0xFF10B981) else Color.Gray,
                        detail = "ملاحظات: ${info.releaseNotes.take(50)}..."
                    )
                } ?: Text("تعذر جلب بيانات Supabase", color = Color.Red, fontSize = 12.sp)
            }
        }

        error?.let {
            item {
                Text("خطأ: $it", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
        }
        
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun BuildInfoCard(
    title: String,
    versionName: String,
    versionCode: Int,
    status: String,
    statusColor: Color,
    detail: String? = null
) {
    AgentNeonCard(accent = statusColor) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        status, 
                        color = statusColor, 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoItem(label = "versionName", value = versionName, modifier = Modifier.weight(1f))
                InfoItem(label = "versionCode", value = versionCode.toString(), modifier = Modifier.weight(1f))
            }
            
            detail?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color.Gray, fontSize = 11.sp, fontFamily = NotoSansFont)
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
