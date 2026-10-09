package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AppDoctorSection(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    var isDiagnosing by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf(AppSelfDoctor.getHistory().take(20)) }
    var currentTab by remember { mutableStateOf(0) } // 0 = Current, 1 = History

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Doctor Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MedicalServices, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("طبيب التطبيق (التشخيص الذاتي)", color = GoldPrimary, fontFamily = TajawalFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("يفحص المفاتيح، الأخطاء، وحالة الأنظمة الحيوية لضمان أفضل أداء.", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isDiagnosing = true
                        coroutineScope.launch {
                            val newReports = AppSelfDoctor.runFullDiagnosis(context)
                            reports = newReports
                            currentTab = 0
                            isDiagnosing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isDiagnosing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepSlate, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("جاري الفحص المعمق...", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepSlate)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فحص شامل الآن", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Spatial Cybernetic HUD Live Status
        SpatialServiceDiagnosticsHub(
            onNavigateToKeys = { /* open keys screen */ }
        )

        TabRow(
            selectedTabIndex = currentTab,
            containerColor = Color.Transparent,
            contentColor = GoldPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[currentTab]),
                    color = GoldPrimary
                )
            }
        ) {
            Tab(
                selected = currentTab == 0,
                onClick = { currentTab = 0 },
                text = { Text("النتائج الحالية", fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            )
            Tab(
                selected = currentTab == 1,
                onClick = { 
                    reports = AppSelfDoctor.getHistory()
                    currentTab = 1 
                },
                text = { Text("سجل التشخيصات", fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            )
        }

        if (reports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (currentTab == 0) "اضغط 'فحص شامل الآن' للبدء" else "لا يوجد سجل للتشخيصات بعد", color = TextSecondary, fontFamily = CairoFont)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(reports) { report ->
                    DoctorReportCard(report = report, context = context)
                }
            }
        }
    }
}

@Composable
fun DoctorReportCard(report: DoctorReport, context: Context) {
    var expanded by remember { mutableStateOf(false) }

    val (icon, color, bgAlpha) = when (report.severity) {
        "حرج" -> Triple(Icons.Default.ErrorOutline, Color(0xFFEF4444), 0.15f) // Red
        "مهم" -> Triple(Icons.Default.WarningAmber, Color(0xFFF5D76E), 0.15f) // Orange
        "تحسين" -> Triple(Icons.Default.TipsAndUpdates, Color(0xFF3B82F6), 0.15f) // Blue
        else -> Triple(Icons.Default.CheckCircleOutline, Color(0xFF10B981), 0.15f) // Green/Info
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = bgAlpha)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.title, color = color, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(report.category, color = color.copy(alpha = 0.8f), fontFamily = NotoSansFont, fontSize = 12.sp)
                }
                Surface(
                    color = color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, color)
                ) {
                    Text(
                        report.severity,
                        color = color,
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = color.copy(alpha = 0.3f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("التشخيص:", color = Color.White, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(report.message, color = Color(0xFFF1F5F9), fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 18.sp)
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text("الإجراء المقترح:", color = GoldPrimary, fontFamily = NotoSansFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(report.suggestedAction, color = Color(0xFFE2E8F0), fontFamily = NotoSansFont, fontSize = 12.sp, lineHeight = 18.sp)

                    if (report.suggestedFixCode != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0B0F19), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(report.suggestedFixCode, color = Color(0xFF34D399), fontFamily = NotoSansFont, fontSize = 12.sp)
                            
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Fix Code", report.suggestedFixCode))
                                    Toast.makeText(context, "تم نسخ الكود 📋", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
