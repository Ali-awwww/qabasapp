package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * مركز استقبال وإدارة بلاغات الأعطال واقتراحات التحسين للمطور والإدارة.
 * مصمم بنظام Spatial HUD Tech وقوالب الردود السريعة والتصدير البرمجي لمطوري البرمجيات.
 */
@Composable
fun BugReportsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val localReports by BugReportRepository.reports.collectAsState()
    var cloudReports by remember { mutableStateOf<List<SupabaseServices.BugReportRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedReportId by remember { mutableStateOf<String?>(null) }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    fun loadAll() {
        isLoading = true
        scope.launch {
            BugReportRepository.init(context)
            if (SupabaseServices.isSupabaseAvailable) {
                cloudReports = SupabaseServices.Database.getAllBugReports()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadAll()
    }

    // دمج البلاغات المحلية والسحابية مع استبعاد التكرار بناءً على الـ ID
    val mergedReports = remember(localReports, cloudReports) {
        val map = mutableMapOf<String, BugReportRepository.BugReportItem>()
        cloudReports.forEach { c ->
            map[c.id] = BugReportRepository.BugReportItem(
                id = c.id,
                screenName = c.screenName,
                description = c.description,
                reportType = c.reportType,
                status = c.status,
                userEmail = c.userEmail,
                localImagePath = null,
                imageUrl = c.imageUrl.takeIf { it.isNotBlank() },
                appVersion = c.appVersion,
                deviceModel = c.deviceInfo,
                developerReply = c.developerReply,
                syncedToCloud = true
            )
        }
        localReports.forEach { l ->
            val existing = map[l.id]
            if (existing != null) {
                map[l.id] = existing.copy(
                    localImagePath = l.localImagePath ?: existing.localImagePath,
                    imageUrl = existing.imageUrl ?: l.imageUrl,
                    developerReply = existing.developerReply ?: l.developerReply
                )
            } else {
                map[l.id] = l
            }
        }
        map.values.sortedByDescending { it.createdAtMillis }
    }

    val filteredReports = remember(mergedReports, selectedTypeFilter, selectedStatusFilter, searchQuery) {
        mergedReports.filter { item ->
            val matchesType = when (selectedTypeFilter) {
                "BUG" -> item.reportType.equals("BUG", true)
                "IMPROVEMENT" -> item.reportType.equals("IMPROVEMENT", true)
                "CONTENT" -> item.reportType.equals("CONTENT", true)
                else -> true
            }
            val matchesStatus = when (selectedStatusFilter) {
                "open" -> item.status == "open"
                "in_progress" -> item.status == "in_progress"
                "resolved" -> item.status == "resolved"
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                item.description.contains(searchQuery, ignoreCase = true) ||
                item.screenName.contains(searchQuery, ignoreCase = true) ||
                item.userEmail.contains(searchQuery, ignoreCase = true)
            }
            matchesType && matchesStatus && matchesSearch
        }
    }

    val selectedReport = remember(mergedReports, selectedReportId) {
        mergedReports.find { it.id == selectedReportId }
    }

    if (selectedReport != null) {
        BugReportManagementDetailView(
            report = selectedReport,
            onBack = {
                AppSoundManager.playClick()
                selectedReportId = null
            },
            onStatusUpdate = { newStatus, note ->
                scope.launch {
                    BugReportRepository.updateReportStatus(context, selectedReport.id, newStatus, note)
                    AppSoundManager.playSuccess()
                    Toast.makeText(context, "تم تحديث حالة البلاغ بنجاح", Toast.LENGTH_SHORT).show()
                    loadAll()
                }
            },
            onDelete = {
                scope.launch {
                    BugReportRepository.deleteReport(context, selectedReport.id)
                    selectedReportId = null
                    AppSoundManager.playClick()
                    Toast.makeText(context, "تم حذف البلاغ", Toast.LENGTH_SHORT).show()
                    loadAll()
                }
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSlate)
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Text(
                            "مركز استقبال وإدارة البلاغات والطلبات",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        )
                    }
                    Text(
                        "استقبال وفحص الأعطال واقتراحات المستخدمين والمزامنة السحابية",
                        color = TextSecondary,
                        fontFamily = CairoFont,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            AppSoundManager.playToggle()
                            scope.launch {
                                isSyncing = true
                                BugReportRepository.syncPendingReports(context)
                                loadAll()
                                isSyncing = false
                                AppSoundManager.playSuccess()
                                Toast.makeText(context, "تمت المزامنة بنجاح 🔄", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.35f), CircleShape)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GoldPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync", tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        }
                    }

                    IconButton(
                        onClick = {
                            AppSoundManager.playClick()
                            loadAll()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // عدادات الإحصائيات (KPIs)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val total = mergedReports.size
                val open = mergedReports.count { it.status == "open" }
                val inProgress = mergedReports.count { it.status == "in_progress" }
                val resolved = mergedReports.count { it.status == "resolved" }

                DevKpiCard("الكل", total.toString(), Color.White, Modifier.weight(1f))
                DevKpiCard("قيد الانتظار", open.toString(), Color(0xFFEF4444), Modifier.weight(1f))
                DevKpiCard("جاري العمل", inProgress.toString(), Color(0xFF38BDF8), Modifier.weight(1f))
                DevKpiCard("تم الحل", resolved.toString(), Color(0xFF10B981), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // شريط البحث
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                placeholder = { Text("بحث في البلاغات أو الشاشات أو البريد...", color = TextSecondary, fontSize = 11.5.sp, fontFamily = CairoFont) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // فلاتر التبويب
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val typeChips = listOf(
                    "ALL" to "الكل (${mergedReports.size})",
                    "BUG" to "🐞 أعطال (${mergedReports.count { it.reportType.equals("BUG", true) }})",
                    "IMPROVEMENT" to "💡 اقتراحات (${mergedReports.count { it.reportType.equals("IMPROVEMENT", true) }})",
                    "CONTENT" to "📜 محتوى (${mergedReports.count { it.reportType.equals("CONTENT", true) }})"
                )

                items(typeChips) { (key, label) ->
                    val isSelected = selectedTypeFilter == key
                    Surface(
                        onClick = {
                            AppSoundManager.playClick()
                            selectedTypeFilter = key
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) GoldPrimary else CardSurface,
                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) DeepSlate else Color.White,
                            fontFamily = CairoFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldPrimary)
                }
            } else if (filteredReports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981).copy(alpha = 0.5f), modifier = Modifier.size(54.dp))
                        Text("لا توجد بلاغات تطابق البحث حالياً 🎉", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("جميع الأنظمة مستقرة ولا توجد طلبات معلقة", color = TextSecondary, fontFamily = CairoFont, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredReports, key = { it.id }) { report ->
                        DevReportCard(
                            report = report,
                            onClick = {
                                AppSoundManager.playClick()
                                selectedReportId = report.id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DevKpiCard(label: String, count: String, countColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count, color = countColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(label, color = TextSecondary, fontFamily = CairoFont, fontSize = 9.5.sp)
        }
    }
}

@Composable
private fun DevReportCard(report: BugReportRepository.BugReportItem, onClick: () -> Unit) {
    val statusColor = when (report.status.lowercase()) {
        "resolved" -> Color(0xFF10B981)
        "in_progress" -> Color(0xFF38BDF8)
        else -> Color(0xFFEF4444)
    }

    val typeColor = when (report.reportType.uppercase()) {
        "BUG" -> Color(0xFFEF4444)
        "UI_OVERFLOW" -> Color(0xFF00E5FF)
        "IMPROVEMENT" -> Color(0xFF10B981)
        else -> GoldPrimary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Box
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(typeColor.copy(alpha = 0.15f))
                    .border(0.8.dp, typeColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (report.reportType.uppercase()) {
                        "BUG" -> Icons.Default.BugReport
                        "UI_OVERFLOW" -> Icons.Default.LayersClear
                        "IMPROVEMENT" -> Icons.Default.Lightbulb
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        report.screenName,
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = CairoFont
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            report.statusLabel,
                            color = statusColor,
                            fontFamily = CairoFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    report.description,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = CairoFont,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "من: ${report.userEmail}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!report.developerReply.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(11.dp))
                            Text("تم الرد", color = GoldPrimary, fontSize = 9.sp, fontFamily = CairoFont)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BugReportManagementDetailView(
    report: BugReportRepository.BugReportItem,
    onBack: () -> Unit,
    onStatusUpdate: (String, String?) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var replyText by remember { mutableStateOf(report.developerReply ?: "") }
    var showPreviewImage by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var activeRemediationProposal by remember { mutableStateOf<AiAutonomousRemediationEngine.AiRemediationProposal?>(null) }
    var isAnalyzingProposal by remember { mutableStateOf(false) }

    val quickReplies = listOf(
        "جزاك الله خيراً وبارك فيك على حرصك ومساهمتك الطيبة في الإبلاغ 🌿، نسأل الله أن يكتب أجرك، جاري الفحص والتدقيق بعناية.",
        "شكر الله لك وسدد خطاك 🤲؛ تم استلام البلاغ ووضعه قيد المتابعة التقنية المباشرة من فريق التطوير.",
        "بارك الله في جهودك الكريمة 💎؛ مساهمتك القيمة تساعدنا في تقديم تجربة أرقى لخدمة كتاب الله والمجتمع.",
        "أحسن الله إليك وأجزل لك المثوبة ✨؛ هل بالإمكان تزويدنا بتفاصيل إضافية لمساعدتنا في تدقيق المسألة؟"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSlate)
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoldPrimary)
                }
                Text("تفاصيل وإدارة البلاغ", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Copy Markdown Issue Report
                IconButton(
                    onClick = {
                        AppSoundManager.playClick()
                        val md = buildString {
                            append("### [${report.reportType}] ${report.screenName}\n\n")
                            append("**الوصف:**\n${report.description}\n\n")
                            append("**بيانات الجهاز والبيئة:**\n")
                            append("- المستخدم: ${report.userEmail}\n")
                            append("- الشاشة: ${report.screenName}\n")
                            append("- الإصدار: ${report.appVersion}\n")
                            append("- الجهاز: ${report.deviceModel}\n")
                            append("- التاريخ: ${report.formattedDate}\n")
                            append("- المعرف: `${report.id}`\n")
                            if (!report.imageUrl.isNullOrBlank()) {
                                append("- الصورة السحابية: ${report.imageUrl}\n")
                            }
                        }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Markdown Issue", md))
                        Toast.makeText(context, "تم نسخ تقرير Markdown لمشاركته مع فريق التطوير 📋", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Markdown", tint = GoldPrimary)
                }

                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // صورة لقطة الشاشة
            val imgSource = report.localImagePath ?: report.imageUrl
            if (!imgSource.isNullOrBlank()) {
                item {
                    val file = File(imgSource)
                    val model = if (file.exists()) file else imgSource

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clickable {
                                AppSoundManager.playClick()
                                showPreviewImage = true
                            },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = model,
                                contentDescription = "Screenshot",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = DeepSlate.copy(alpha = 0.85f),
                                border = BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Text("تكبير الشاشة", color = GoldPrimary, fontSize = 10.sp, fontFamily = CairoFont)
                                }
                            }
                        }
                    }
                }
            }

            // بطاقة البيانات والمعلومات التقنية
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailInfoRow("نوع البلاغ", report.typeLabel)
                        DetailInfoRow("الشاشة المصدر", report.screenName)
                        DetailInfoRow("المستخدم", report.userEmail)
                        if (report.appVersion.isNotBlank()) DetailInfoRow("إصدار التطبيق", report.appVersion)
                        if (report.deviceModel.isNotBlank()) DetailInfoRow("الجهاز", report.deviceModel)
                        DetailInfoRow("التاريخ", report.formattedDate)
                        DetailInfoRow("المعرف الفريد", report.id)
                    }
                }
            }

            // نص الوصف
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("وصف المشكلة / الاقتراح:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(report.description, color = Color.White, fontFamily = CairoFont, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }

            // زر المعالجة الآلية الذكية واستكشاف الأخطاء مع طلب الموافقة الصريحة
            item {
                Button(
                    onClick = {
                        AppSoundManager.playActivate()
                        isAnalyzingProposal = true
                        scope.launch {
                            val prop = AiAutonomousRemediationEngine.analyzeAndProposeFix(
                                context = context,
                                title = "بلاغ عطل: ${report.typeLabel} - ${report.screenName}",
                                description = report.description
                            )
                            isAnalyzingProposal = false
                            activeRemediationProposal = prop
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "🤖 تحليل الذكاء وتوليد إصلاح GitHub (مع بوابة الموافقة 🛡️)",
                        color = DeepSlate,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // صندوق رد المطور وقوالب الرد السريعة
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("💬 رد وتحديث المطور (يظهر في سجل المستخدم):", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        // قوالب الرد السريع
                        Text("قوالب رد سريعة:", color = TextSecondary, fontSize = 10.sp, fontFamily = CairoFont)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(quickReplies) { preset ->
                                Surface(
                                    onClick = {
                                        AppSoundManager.playClick()
                                        replyText = preset
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Text(preset, color = Color.White, fontSize = 10.sp, fontFamily = CairoFont, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("اكتب رداً أو تحديثاً للمستخدم...", color = TextSecondary, fontSize = 11.sp, fontFamily = CairoFont) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // أزرار تغيير الحالة
        Text("تحديث حالة البلاغ وحفظ الرد:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statuses = listOf(
                "open" to ("🔴 قيد المراجعة" to Color(0xFFEF4444)),
                "in_progress" to ("🟡 جاري الإصلاح" to Color(0xFF38BDF8)),
                "resolved" to ("🟢 تم الحل" to Color(0xFF10B981))
            )

            statuses.forEach { (statusKey, pair) ->
                val (title, color) = pair
                val isSelected = report.status == statusKey
                Button(
                    onClick = { onStatusUpdate(statusKey, replyText.takeIf { it.isNotBlank() }) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) color else CardSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isSelected) color else Color.White.copy(alpha = 0.1f))
                ) {
                    Text(
                        title,
                        fontSize = 10.sp,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) DeepSlate else Color.White
                    )
                }
            }
        }
    }

    // مؤشر تحليل الذكاء الاصطناعي الجاري
    if (isAnalyzingProposal) {
        Dialog(onDismissRequest = {}) {
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = Color(0xFF0F172A).copy(alpha = 0.95f),
                borderColor = Color(0xFF06B6D4).copy(alpha = 0.6f),
                borderWidth = 1.2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF06B6D4),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "AI_BUG_DIAGNOSIS...",
                        color = Color(0xFF06B6D4),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "جارٍ فحص البلاغ ومقارنته بهيكل المستودع وتجهيز مقترح التعديل 🛰️",
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // بوابة الموافقة والتفويض الصريح (Explicit Consent Gate)
    activeRemediationProposal?.let { proposal ->
        SpatialAiRemediationApprovalDialog(
            proposal = proposal,
            onDismiss = { activeRemediationProposal = null },
            onApprovedAndExecuted = { executionResult ->
                activeRemediationProposal = null
                val updatedReply = "🛡️ [إصلاح معتمد من المطور]: $executionResult"
                replyText = updatedReply
                onStatusUpdate("in_progress", updatedReply)
            }
        )
    }

    if (showPreviewImage) {
        val imgSource = report.localImagePath ?: report.imageUrl
        if (!imgSource.isNullOrBlank()) {
            Dialog(onDismissRequest = { showPreviewImage = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DeepSlate)
                        .border(1.dp, GoldPrimary, RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val file = File(imgSource)
                        val model = if (file.exists()) file else imgSource

                        AsyncImage(
                            model = model,
                            contentDescription = "Zoomed",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 500.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                AppSoundManager.playClick()
                                showPreviewImage = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("إغلاق", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // تأكيد الحذف
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = DeepSlate,
            title = {
                Text("حذف البلاغ", color = Color(0xFFEF4444), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text("هل أنت متأكد من رغبتك في حذف هذا البلاغ بشكل نهائي من السجل المحلي والسحابي؟", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("حذف نهائي", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء", color = Color.White, fontFamily = CairoFont)
                }
            }
        )
    }
}

@Composable
private fun DetailInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextSecondary, fontFamily = CairoFont, fontSize = 11.sp)
        Text(value, color = Color.White, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
