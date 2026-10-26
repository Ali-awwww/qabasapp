package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * نافذة وقسم المستخدم لاستقبال ومتابعة جميع البلاغات والاقتراحات والطلبات المرسلة.
 * مصممة بنمط Cybernetic Glassmorphism الفاخر مع مؤشرات التتبع الزمني (Timeline Tracker).
 */
@Composable
fun UserReportsDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allReports by BugReportRepository.reports.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedImageForPreview by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    val currentUser by SupabaseServices.Auth.currentUser.collectAsState()

    LaunchedEffect(Unit) {
        BugReportRepository.loadLocalReports(context)
        // المزامنة التلقائية الدورية أثناء فتح النافذة
        while(true) {
            currentUser?.email?.let { email ->
                BugReportRepository.refreshUserReports(context, email)
            }
            kotlinx.coroutines.delay(15000) // كل 15 ثانية
        }
    }

    val filteredReports = remember(allReports, selectedFilter) {
        when (selectedFilter) {
            "BUG" -> allReports.filter { it.reportType.equals("BUG", ignoreCase = true) || it.reportType in listOf("UI_OVERFLOW", "MEMORY_PRESSURE", "NETWORK_OFFLINE", "AUDIO_ISSUE", "SLOW_RENDER") }
            "DIAGNOSTIC" -> allReports.filter { it.description.contains("🔬") || it.description.contains("رصد آلي") || it.reportType in listOf("UI_OVERFLOW", "MEMORY_PRESSURE", "NETWORK_OFFLINE", "AUDIO_ISSUE", "SLOW_RENDER", "DIAGNOSTIC_SNAPSHOT") }
            "IMPROVEMENT" -> allReports.filter { it.reportType.equals("IMPROVEMENT", ignoreCase = true) }
            "CONTENT" -> allReports.filter { it.reportType.equals("CONTENT", ignoreCase = true) }
            else -> allReports
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(26.dp),
            borderColor = GoldPrimary.copy(alpha = 0.55f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header Bar with Spatial Glow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            AppSoundManager.playClick()
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.TrackChanges, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                "سجل ومتابعة طلباتي",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Text(
                            "متابعة حالة الأعطال والاقتراحات وردود فريق التطوير",
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            AppSoundManager.playToggle()
                            scope.launch {
                                isSyncing = true
                                BugReportRepository.syncPendingReports(context)
                                isSyncing = false
                                Toast.makeText(context, "تم تحديث السجل والمزامنة السحابية 🔄", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.3f), CircleShape)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = GoldPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Sync",
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // KPI Counter Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val openCount = allReports.count { it.status == "open" }
                    val inProgressCount = allReports.count { it.status == "in_progress" }
                    val resolvedCount = allReports.count { it.status == "resolved" }

                    UserKpiPill("الإجمالي", allReports.size.toString(), Color.White, Modifier.weight(1f))
                    UserKpiPill("قيد المراجعة", openCount.toString(), Color(0xFFF59E0B), Modifier.weight(1f))
                    UserKpiPill("جاري العمل", inProgressCount.toString(), Color(0xFF38BDF8), Modifier.weight(1f))
                    UserKpiPill("تم الحل", resolvedCount.toString(), Color(0xFF10B981), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Filter Chips Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isBugOrTech = { r: BugReportRepository.BugReportItem ->
                        r.reportType.equals("BUG", true) || r.reportType in listOf("UI_OVERFLOW", "MEMORY_PRESSURE", "NETWORK_OFFLINE", "AUDIO_ISSUE")
                    }
                    val isDiag = { r: BugReportRepository.BugReportItem ->
                        r.description.contains("🔬") || r.description.contains("رصد آلي") || r.reportType in listOf("UI_OVERFLOW", "MEMORY_PRESSURE", "NETWORK_OFFLINE", "AUDIO_ISSUE")
                    }

                    val filterChips = listOf(
                        "ALL" to "الكل (${allReports.size})",
                        "BUG" to "🐞 أعطال (${allReports.count(isBugOrTech)})",
                        "DIAGNOSTIC" to "🔬 تشخيص (${allReports.count(isDiag)})",
                        "IMPROVEMENT" to "💡 اقتراحات (${allReports.count { it.reportType.equals("IMPROVEMENT", true) }})",
                        "CONTENT" to "📜 محتوى (${allReports.count { it.reportType.equals("CONTENT", true) }})"
                    )

                    items(filterChips) { (key, label) ->
                        val isSelected = selectedFilter == key
                        Surface(
                            onClick = {
                                AppSoundManager.playClick()
                                selectedFilter = key
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.12f)
                            )
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) DeepSlate else Color.White,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Reports List
                if (filteredReports.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary.copy(alpha = 0.1f))
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Inbox,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text(
                                "لا توجد بلاغات مسجلة في هذا التبويب",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "عند الإبلاغ عن أي خلل أو اقتراح، سيظهر هنا فوراً مع مراحل التتبع وردود فريق التطوير 🚀",
                                color = TextSecondary,
                                fontFamily = CairoFont,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredReports, key = { it.id }) { report ->
                            UserReportCard(
                                report = report,
                                onDismissRequest = onDismissRequest,
                                onPreviewImage = { imgPath ->
                                    AppSoundManager.playClick()
                                    selectedImageForPreview = imgPath
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Image Preview
    if (selectedImageForPreview != null) {
        Dialog(onDismissRequest = { selectedImageForPreview = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DeepSlate)
                    .border(1.2.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val file = File(selectedImageForPreview!!)
                    val model = if (file.exists()) file else selectedImageForPreview

                    AsyncImage(
                        model = model,
                        contentDescription = "Enlarged Screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            AppSoundManager.playClick()
                            selectedImageForPreview = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("إغلاق المعاينة", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserKpiPill(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, color = TextSecondary, fontFamily = CairoFont, fontSize = 9.sp)
        }
    }
}

@Composable
private fun UserReportCard(
    report: BugReportRepository.BugReportItem,
    onDismissRequest: () -> Unit,
    onPreviewImage: (String) -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val statusColor = when (report.status.lowercase()) {
        "resolved" -> Color(0xFF10B981)
        "in_progress" -> Color(0xFF38BDF8)
        else -> Color(0xFFF59E0B)
    }

    val typeColor = when (report.reportType.uppercase()) {
        "BUG" -> Color(0xFFEF4444)
        "UI_OVERFLOW" -> Color(0xFF00E5FF)
        "IMPROVEMENT" -> Color(0xFF10B981)
        else -> GoldPrimary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111726).copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsating Status Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (report.status == "in_progress") statusColor.copy(alpha = alphaAnim) else statusColor,
                                    CircleShape
                                )
                        )
                        Text(
                            report.statusLabel,
                            color = statusColor,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                    }
                }

                Text(
                    report.typeLabel,
                    color = typeColor,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Progress Tracker (Timeline HUD)
            TimelineTrackerBar(currentStatus = report.status)

            Spacer(modifier = Modifier.height(10.dp))

            // Screen name & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                    Text(
                        "الشاشة: ${report.screenName}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    report.formattedDate,
                    color = TextSecondary,
                    fontFamily = CairoFont,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description Body
            Text(
                text = report.description,
                color = Color.White,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            // Attached Screenshot Preview if exists
            val imageSource = report.localImagePath ?: report.imageUrl
            if (!imageSource.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(0.8.dp, GoldPrimary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable { onPreviewImage(imageSource) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val file = File(imageSource)
                    val model = if (file.exists()) file else imageSource

                    AsyncImage(
                        model = model,
                        contentDescription = "Screenshot",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(0.5.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "📸 لقطة الشاشة المرفقة",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            "انقر للمعاينة بحجم الشاشة الكامل",
                            color = TextSecondary,
                            fontFamily = CairoFont,
                            fontSize = 10.sp
                        )
                    }

                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom", tint = GoldPrimary, modifier = Modifier.size(20.dp))
                }
            }

            // Developer Reply Note
            if (!report.developerReply.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoldPrimary.copy(alpha = 0.09f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Text(
                                "💬 رد وتحديث فريق التطوير:",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            report.developerReply,
                            color = Color.White.copy(alpha = 0.95f),
                            fontFamily = CairoFont,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Actions & Sync Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Actions (Copy / Share)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            AppSoundManager.playClick()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Report ID", report.id)
                            cm.setPrimaryClip(clip)
                            Toast.makeText(context, "تم نسخ معرّف البلاغ للمطالبة والدعم 📋", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                            Text("نسخ المعرف", color = TextSecondary, fontSize = 9.sp, fontFamily = CairoFont)
                        }
                    }

                    Surface(
                        onClick = {
                            AppSoundManager.playClick()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "بلاغ تطبيق قبس: ${report.typeLabel}")
                                putExtra(Intent.EXTRA_TEXT, "بلاغ قبس:\nالشاشة: ${report.screenName}\nالوصف: ${report.description}\nالحالة: ${report.statusLabel}\nالمعرف: ${report.id}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة تفاصيل البلاغ"))
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                            Text("مشاركة", color = TextSecondary, fontSize = 9.sp, fontFamily = CairoFont)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        onClick = {
                            AppSoundManager.playClick()
                            // فتح المحادثة المباشرة المرتبطة بالبلاغ
                            AppNavigator.navigateToChat(report.userEmail, report.id)
                            onDismissRequest() // إغلاق النافذة لفتح المحادثة
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = GoldPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(11.dp))
                            Text("فتح المحادثة 💬", color = GoldPrimary, fontSize = 9.sp, fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Cloud Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (report.syncedToCloud) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مُرسل وموثق سحابياً", color = Color(0xFF10B981), fontSize = 9.5.sp, fontFamily = CairoFont)
                    } else {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("محفوظ محلياً (قيد المزامنة)", color = Color(0xFFF59E0B), fontSize = 9.5.sp, fontFamily = CairoFont)
                    }
                }
            }
        }
    }
}

/**
 * شريط تتبع زمني للمستخدم (Timeline Tracker):
 * 1. استلام الطلب -> 2. قيد المراجعة -> 3. جاري الإصلاح -> 4. تم الحل
 */
@Composable
private fun TimelineTrackerBar(currentStatus: String) {
    val step = when (currentStatus.lowercase()) {
        "resolved" -> 3
        "in_progress" -> 2
        else -> 1
    }

    val stages = listOf(
        "تم الاستلام",
        "المراجعة",
        "جاري العمل",
        "تم الحل"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.25f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            stages.forEachIndexed { index, title ->
                val isReached = index <= step
                val isCurrent = index == step
                val activeColor = if (step == 3) Color(0xFF10B981) else if (step == 2) Color(0xFF38BDF8) else Color(0xFFF59E0B)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (isReached) activeColor else Color.White.copy(alpha = 0.15f))
                            .border(1.dp, if (isCurrent) Color.White else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isReached) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(9.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        title,
                        color = if (isReached) Color.White else TextSecondary.copy(alpha = 0.6f),
                        fontFamily = CairoFont,
                        fontSize = 9.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (index < stages.size - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.5.dp)
                            .padding(horizontal = 4.dp)
                            .background(if (index < step) activeColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f))
                    )
                }
            }
        }
    }
}
