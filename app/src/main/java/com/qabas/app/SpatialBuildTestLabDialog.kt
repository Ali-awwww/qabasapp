package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 🧪 مختبر فحص سلامة البناء والتحقق التركيبي ونسخ المستودعات
 * Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialBuildTestLabDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AgentPrefs.instance(context) }

    val activeRepo = remember { RepoVaultManager.getActiveRepo(context) }
    val owner = activeRepo?.owner ?: prefs.getString("build_center_owner", "") ?: ""
    val repo = activeRepo?.repo ?: prefs.getString("build_center_repo", "") ?: ""
    val token = activeRepo?.token ?: prefs.getString("build_center_token", "") ?: ""
    val defaultBranch = activeRepo?.defaultBranch ?: "main"

    var selectedTab by remember { mutableStateOf(0) } // 0: فحص الكود والبناء, 1: GitHub CI/CD, 2: نسخ وتفريغ المستودع

    // حالات الفحص المحلي
    var isRunningLocalScan by remember { mutableStateOf(false) }
    var localReport by remember { mutableStateOf<BuildVerificationReport?>(null) }

    // حالات GitHub Actions CI
    var isFetchingCi by remember { mutableStateOf(false) }
    var isTriggeringCi by remember { mutableStateOf(false) }
    var ciStatus by remember { mutableStateOf<CiWorkflowStatus?>(null) }
    var isInstallingCiWorkflow by remember { mutableStateOf(false) }

    // حالات نسخ وتفريغ المستودع
    var isForking by remember { mutableStateOf(false) }
    var isDuplicating by remember { mutableStateOf(false) }
    var duplicateProgressText by remember { mutableStateOf("") }
    var targetNewRepoName by remember { mutableStateOf("${repo}-clone") }
    var forkSuccessResult by remember { mutableStateOf<RepoForkResult?>(null) }
    var duplicateSuccessName by remember { mutableStateOf<String?>(null) }

    // إجراء فحص محلي أولي عند الفتح
    LaunchedEffect(Unit) {
        isRunningLocalScan = true
        localReport = BuildVerificationEngine.runLocalSyntaxAndSanityCheck(context)
        isRunningLocalScan = false

        if (owner.isNotBlank() && repo.isNotBlank()) {
            isFetchingCi = true
            ciStatus = BuildVerificationEngine.fetchGitHubCiStatus(owner, repo, token)
            isFetchingCi = false
        }
    }

    // نبض الإطار السيبراني المتوهج
    val infiniteTransition = rememberInfiniteTransition(label = "build_lab_pulse")
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "build_pulse_val"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF030712).copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.95f)
                    .border(
                        1.4.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF06B6D4).copy(alpha = borderPulse),
                                Color(0xFF10B981).copy(alpha = borderPulse * 0.85f),
                                GoldPrimary.copy(alpha = borderPulse * 0.7f)
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .testTag("spatial_build_test_lab_dialog"),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = Color(0xFF090D1A).copy(alpha = 0.97f),
                borderColor = Color(0xFF06B6D4).copy(alpha = 0.35f),
                borderWidth = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // ═══════════════════════════════════════════════════
                    // 1. ترويسة المختبر
                    // ═══════════════════════════════════════════════════
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A))
                                .border(1.5.dp, Brush.sweepGradient(listOf(Color(0xFF10B981), Color(0xFF06B6D4))), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Biotech,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "مختبر فحص سلامة البناء والكود 🧪",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.7f))
                                ) {
                                    Text(
                                        "BUILD_LAB_PRO",
                                        color = Color(0xFF6EE7B7),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Text(
                                "التحقق التركيبي لكود Kotlin، فحص سلامة البناء، واختبار وتفريغ المستودعات",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ═══════════════════════════════════════════════════
                    // 2. شريط التبويبات الفضائي
                    // ═══════════════════════════════════════════════════
                    val tabs = listOf(
                        Triple("فحص الكود والبناء ⚡", Icons.Default.Code, 0),
                        Triple("سحابة GitHub CI 🚀", Icons.Default.CloudSync, 1),
                        Triple("نسخ وتفريغ المستودع 📦", Icons.Default.ForkRight, 2)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabs.forEach { (title, icon, idx) ->
                            val isSel = selectedTab == idx
                            Surface(
                                color = if (isSel) Color(0xFF06B6D4).copy(alpha = 0.2f) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) Color(0xFF06B6D4) else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.clickable { selectedTab = idx }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = if (isSel) Color(0xFF06B6D4) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        title,
                                        color = if (isSel) Color.White else Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ═══════════════════════════════════════════════════
                    // 3. المحتوى حسب التبويب
                    // ═══════════════════════════════════════════════════
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // ==========================================
                                // تبويب 1: فحص الكود المحلي والسلامة التركيبية
                                // ==========================================
                                localReport?.let { report ->
                                    // مقياس الصحة البرمجية
                                    Surface(
                                        color = Color(0xFF070B14),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, if (report.healthScore >= 90) Color(0xFF10B981) else Color(0xFFF59E0B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "مؤشر سلامة البناء (BUILD_HEALTH_INDEX)",
                                                        color = Color(0xFF94A3B8),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        report.statusText,
                                                        color = if (report.healthScore >= 90) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                        fontFamily = CairoFont,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                }

                                                Surface(
                                                    color = if (report.healthScore >= 90) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                    shape = CircleShape,
                                                    border = BorderStroke(1.5.dp, if (report.healthScore >= 90) Color(0xFF10B981) else Color(0xFFF59E0B))
                                                ) {
                                                    Text(
                                                        "${report.healthScore}%",
                                                        color = Color.White,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            LinearProgressIndicator(
                                                progress = { report.healthScore / 100f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = if (report.healthScore >= 90) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                trackColor = Color(0xFF1E293B)
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("الملفات المفحوصة: ${report.totalFilesScanned}", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                                                Text("الأخطاء: ${report.syntaxErrors.size}", color = if (report.syntaxErrors.isEmpty()) Color(0xFF10B981) else Color(0xFFEF4444), fontFamily = CairoFont, fontSize = 10.5.sp)
                                                Text("التحذيرات: ${report.warnings.size}", color = Color(0xFFF59E0B), fontFamily = CairoFont, fontSize = 10.5.sp)
                                            }
                                        }
                                    }

                                    // قائمة الأخطاء والملاحظات إن وجدت
                                    if (report.syntaxErrors.isNotEmpty() || report.manifestIssues.isNotEmpty() || report.warnings.isNotEmpty()) {
                                        Text("📋 تفاصيل الملاحظات المكتشفة:", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)

                                        (report.syntaxErrors + report.manifestIssues + report.warnings).forEach { issue ->
                                            Surface(
                                                color = Color(0xFF0F172A),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    0.8.dp,
                                                    when (issue.severity) {
                                                        BuildIssueSeverity.ERROR -> Color(0xFFEF4444)
                                                        BuildIssueSeverity.WARNING -> Color(0xFFF59E0B)
                                                        BuildIssueSeverity.INFO -> Color(0xFF06B6D4)
                                                    }
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            if (issue.severity == BuildIssueSeverity.ERROR) Icons.Default.Error else Icons.Default.Warning,
                                                            contentDescription = null,
                                                            tint = if (issue.severity == BuildIssueSeverity.ERROR) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            "${issue.file}${if (issue.line > 0) ":${issue.line}" else ""}",
                                                            color = Color(0xFF06B6D4),
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(issue.message, color = Color.White, fontFamily = CairoFont, fontSize = 11.sp)
                                                    if (issue.suggestion.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text("💡 نصيحة الحل: ${issue.suggestion}", color = Color(0xFF6EE7B7), fontFamily = CairoFont, fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text("كافة الملفات سليمة 100% ولا توجد أخطاء تركيبية ✓", color = Color(0xFF10B981), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("الكود خالي من صراعات Git والأقواس متوازنة ومستقرة تماماً.", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // زر إعادة الفحص
                                Button(
                                    onClick = {
                                        isRunningLocalScan = true
                                        scope.launch {
                                            localReport = BuildVerificationEngine.runLocalSyntaxAndSanityCheck(context)
                                            isRunningLocalScan = false
                                            Toast.makeText(context, "تم اكتمال الفحص التركيبي بنجاح ⚡", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isRunningLocalScan,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                                ) {
                                    if (isRunningLocalScan) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إعادة تشغيل الفحص التركيبي الشامل 🔍", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }

                            1 -> {
                                // ==========================================
                                // تبويب 2: سحابة GitHub Actions CI
                                // ==========================================
                                Surface(
                                    color = Color(0xFF070B14),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("حالة البناء السحابي (GitHub Actions CI)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                Text("المستودع: ${if (owner.isNotBlank()) "$owner/$repo" else "غير مهيأ"}", color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        ciStatus?.let { ci ->
                                            Surface(
                                                color = Color(0xFF0F172A),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    when {
                                                        ci.isPassed -> Color(0xFF10B981)
                                                        ci.isRunning -> Color(0xFF06B6D4)
                                                        ci.isFailed -> Color(0xFFEF4444)
                                                        else -> Color(0xFF94A3B8)
                                                    }
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            ci.workflowName,
                                                            color = Color.White,
                                                            fontFamily = CairoFont,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                        Surface(
                                                            color = when {
                                                                ci.isPassed -> Color(0xFF10B981).copy(alpha = 0.2f)
                                                                ci.isRunning -> Color(0xFF06B6D4).copy(alpha = 0.2f)
                                                                ci.isFailed -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                                                else -> Color(0xFF94A3B8).copy(alpha = 0.2f)
                                                            },
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                when {
                                                                    ci.isPassed -> "PASSED ✓"
                                                                    ci.isRunning -> "RUNNING ⏳"
                                                                    ci.isFailed -> "FAILED ❌"
                                                                    else -> ci.status.uppercase()
                                                                },
                                                                color = when {
                                                                    ci.isPassed -> Color(0xFF6EE7B7)
                                                                    ci.isRunning -> Color(0xFF22D3EE)
                                                                    ci.isFailed -> Color(0xFFFCA5A5)
                                                                    else -> Color.White
                                                                },
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("الفرع: ${ci.branch} | آخر فحص: ${ci.updatedAt}", color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                                    Text("رسالة الالتزام: ${ci.commitMessage}", color = Color(0xFFCBD5E1), fontFamily = CairoFont, fontSize = 10.5.sp)
                                                }
                                            }
                                        } ?: run {
                                            Text(
                                                "لم يتم العثور على تشغيل سابق لسير عمل CI أو لم يتم تثبيت سير العمل بعد.",
                                                color = Color(0xFF94A3B8),
                                                fontFamily = CairoFont,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // أزرار تشغيل وتثبيت سير العمل
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (owner.isBlank() || repo.isBlank() || token.isBlank()) {
                                                Toast.makeText(context, "يرجى ضبط بيانات المستودع والـ Token أولاً", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isTriggeringCi = true
                                            scope.launch {
                                                val ok = BuildVerificationEngine.triggerWorkflowDispatch(owner, repo, token, branch = defaultBranch)
                                                isTriggeringCi = false
                                                if (ok) {
                                                    Toast.makeText(context, "تم إطلاق البناء السحابي في GitHub بنجاح 🚀", Toast.LENGTH_LONG).show()
                                                    ciStatus = BuildVerificationEngine.fetchGitHubCiStatus(owner, repo, token)
                                                } else {
                                                    Toast.makeText(context, "تعذر إطلاق البناء، تحقق من وجود ملف سير العمل وصلاحيات workflow", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        enabled = !isTriggeringCi && owner.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                                    ) {
                                        if (isTriggeringCi) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("إطلاق CI الآن 🚀", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (owner.isBlank() || repo.isBlank() || token.isBlank()) {
                                                Toast.makeText(context, "يرجى ضبط بيانات المستودع أولاً", Toast.LENGTH_SHORT).show()
                                                return@OutlinedButton
                                            }
                                            isInstallingCiWorkflow = true
                                            scope.launch {
                                                val client = GitHubRepoClient(owner, repo, token)
                                                val ok = BuildVerificationEngine.ensureDefaultCiWorkflow(client, defaultBranch)
                                                isInstallingCiWorkflow = false
                                                if (ok) {
                                                    Toast.makeText(context, "تم إنشاء ملف سير العمل android_build.yml بنجاح 🛠️", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "تعذر إنشاء الملف، تحقق من الصلاحيات", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        enabled = !isInstallingCiWorkflow && owner.isNotBlank(),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE))
                                    ) {
                                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تثبيت سير العمل", fontFamily = CairoFont, fontSize = 10.5.sp)
                                    }
                                }
                            }

                            2 -> {
                                // ==========================================
                                // تبويب 3: نسخ وتفريغ واستنساخ المستودع
                                // ==========================================
                                Surface(
                                    color = Color(0xFF070B14),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ForkRight, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("تفريغ ونسخ المستودع (Fork & Clone Engine)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                Text("نسخ المستودع إلى حسابك، أو إنشاء نسخة مكررة متكاملة لجميع الملفات", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // 1. تفريع عبر Fork
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (owner.isBlank() || repo.isBlank() || token.isBlank()) {
                                                        Toast.makeText(context, "يرجى ضبط بيانات المستودع والـ Token أولاً", Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    isForking = true
                                                    scope.launch {
                                                        val client = GitHubRepoClient(owner, repo, token)
                                                        val fork = client.forkRepository()
                                                        isForking = false
                                                        if (fork != null) {
                                                            forkSuccessResult = fork
                                                            // إضافة للـ Vault
                                                            val entry = RepoVaultEntry(
                                                                owner = fork.owner,
                                                                repo = fork.name,
                                                                token = token,
                                                                customName = "Fork: ${fork.name}"
                                                            )
                                                            RepoVaultManager.addOrUpdateRepo(context, entry, setAsActive = false)
                                                            Toast.makeText(context, "تم عمل Fork بنجاح: ${fork.fullName} 🍴", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            Toast.makeText(context, "تعذر عمل Fork، تحقق من الصلاحيات والرمز", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                enabled = !isForking && owner.isNotBlank(),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                            ) {
                                                if (isForking) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.ForkRight, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("عمل Fork للمستودع 🍴", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val zipUrl = GitHubRepoClient(owner, repo, token).getZipballUrl(defaultBranch)
                                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clip.setPrimaryClip(ClipData.newPlainText("Zipball URL", zipUrl))
                                                    Toast.makeText(context, "تم نسخ رابط أرشيف Zipball للكليب بورد 📦", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.6f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE))
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("رابط Zipball", fontFamily = CairoFont, fontSize = 10.5.sp)
                                            }
                                        }

                                        forkSuccessResult?.let { fork ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(0.8.dp, Color(0xFF10B981))
                                            ) {
                                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("المستودع المفرّع جاهز: ${fork.fullName}", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // 2. استنساخ كامل للملفات إلى مستودع جديد بالكامل
                                        Text("استنساخ كامل لكافة الملفات إلى مستودع جديد:", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        Spacer(modifier = Modifier.height(6.dp))

                                        OutlinedTextField(
                                            value = targetNewRepoName,
                                            onValueChange = { targetNewRepoName = it },
                                            label = { Text("اسم المستودع الجديد المراد إنشاؤه", fontFamily = CairoFont, fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF10B981),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Button(
                                            onClick = {
                                                if (owner.isBlank() || repo.isBlank() || token.isBlank() || targetNewRepoName.isBlank()) {
                                                    Toast.makeText(context, "يرجى كتابة اسم المستودع الجديد أولاً", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                isDuplicating = true
                                                duplicateProgressText = "بدء عملية الاستنساخ..."
                                                scope.launch {
                                                    val client = GitHubRepoClient(owner, repo, token)
                                                    val createdFull = client.duplicateRepository(
                                                        targetRepoName = targetNewRepoName.trim(),
                                                        isPrivate = true,
                                                        onProgress = { prog -> duplicateProgressText = prog }
                                                    )
                                                    isDuplicating = false
                                                    if (createdFull != null) {
                                                        duplicateSuccessName = createdFull
                                                        val parts = createdFull.split("/")
                                                        val entry = RepoVaultEntry(
                                                            owner = parts[0],
                                                            repo = parts[1],
                                                            token = token,
                                                            customName = parts[1]
                                                        )
                                                        RepoVaultManager.addOrUpdateRepo(context, entry, setAsActive = false)
                                                        Toast.makeText(context, "تم استنساخ المستودع بنجاح: $createdFull 📦", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "تعذر استنساخ المستودع، تحقق من الصلاحيات", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            enabled = !isDuplicating && targetNewRepoName.isNotBlank(),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                                        ) {
                                            if (isDuplicating) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(duplicateProgressText, color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp)
                                                }
                                            } else {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("بدء الاستنساخ السحابي الكامل 📦", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            }
                                        }

                                        duplicateSuccessName?.let { dup ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(0.8.dp, Color(0xFF10B981))
                                            ) {
                                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("تم إنشاء النسخة في الخزانة: $dup", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ═══════════════════════════════════════════════════
                    // 4. زر الإغلاق والرجوع
                    // ═══════════════════════════════════════════════════
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text("إغلاق المختبر ✓", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
