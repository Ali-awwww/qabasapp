package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.*

/**
 * سجل التدقيق: يستعرض أثر الإجراءات الحساسة (المحفوظ محلياً + المدفوع سحابياً).
 */
@Composable
fun AuditLogSection(context: Context) {
    var entries by remember { mutableStateOf(AuditLogger.readLocal(context)) }
    var chainState by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("الكل") }

    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(AuditLogger.buildCsv(entries).toByteArray()) }
            Toast.makeText(context, "صُدّر سجل التدقيق CSV ✅", Toast.LENGTH_SHORT).show()
            AuditLogger.log(context, "audit_export_csv", "تصدير ${entries.size} مدخل")
        }
    }

    fun refresh() {
        entries = AuditLogger.readLocal(context)
        chainState = AuditLogger.verifyChain(context)
    }

    val categories = listOf("الكل", "أمان ورتب", "مفاتيح وسحابة", "نظام وبناء")

    val filteredEntries = entries.filter { e ->
        val matchesQuery = searchQuery.isBlank() || 
            e.action.contains(searchQuery, ignoreCase = true) ||
            e.detail.contains(searchQuery, ignoreCase = true) ||
            e.actor.contains(searchQuery, ignoreCase = true)

        val matchesCat = when (filterCategory) {
            "أمان ورتب" -> e.action.contains("role", true) || e.action.contains("user", true) || e.action.contains("suspend", true) || e.action.contains("promo", true)
            "مفاتيح وسحابة" -> e.action.contains("key", true) || e.action.contains("cloud", true) || e.action.contains("supabase", true)
            "نظام وبناء" -> e.action.contains("build", true) || e.action.contains("system", true) || e.action.contains("clean", true) || e.action.contains("audit", true)
            else -> true
        }
        matchesQuery && matchesCat
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("سجل التدقيق (Audit Log)", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("كل إجراء حساس موثّق ومشفّر بسلسلة بصمات", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 11.sp)
                chainState?.let { broken ->
                    Text(
                        if (broken < 0) "سلسلة البصمات سليمة 🛡️ ✓" else "تحذير: كسر في السلسلة عند المدخل #$broken",
                        color = if (broken < 0) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { csvExport.launch("qabas_audit_${System.currentTimeMillis()}.csv") },
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                ) {
                    Text("CSV", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { refresh() },
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                ) {
                    Text("تحديث", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp)
                }
            }
        }

        // Search + Filter Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("بحث في سجلات التدقيق...", color = Color.Gray, fontFamily = CairoFont, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = filterCategory == cat
                Surface(
                    color = if (isSelected) GoldPrimary.copy(alpha = 0.2f) else CardSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF1E293B)),
                    modifier = Modifier.clickable { filterCategory = cat }
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) GoldPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = CairoFont,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (entries.isEmpty()) "لا يوجد أثر تدقيق بعد — نفّذ إجراءً حساساً (رتبة/إيقاف/كود/بث) ليُسجَّل." else "لا توجد نتائج مطابقة للبحث.",
                    color = TextSecondary, fontFamily = NotoSansFont, fontSize = 13.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredEntries) { e ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.action, color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(AuditLogger.formatTime(e.timeMs), color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
                            }
                            Text(e.detail, color = Color.White, fontFamily = NotoSansFont, fontSize = 12.sp)
                            Text("بواسطة: ${e.actor}", color = TextSecondary, fontFamily = NotoSansFont, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
