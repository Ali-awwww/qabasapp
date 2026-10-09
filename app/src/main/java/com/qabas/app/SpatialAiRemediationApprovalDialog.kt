package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * 🛸 SpatialAiRemediationApprovalDialog — بوابة تفويض واعتماد المعالجة الآلية الذكية
 * تصميم: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 * يشترط الموافقة الصريحة للمطور قبل تطبيق أي تعديل أو إرسال لـ GitHub.
 */
@Composable
fun SpatialAiRemediationApprovalDialog(
    proposal: AiAutonomousRemediationEngine.AiRemediationProposal,
    onDismiss: () -> Unit,
    onApprovedAndExecuted: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isExecuting by remember { mutableStateOf(false) }
    var executionStatusText by remember { mutableStateOf("") }
    var executionError by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: تشخيص وإصلاح, 1: الكود المقترح, 2: تفاصيل GitHub

    val infiniteTransition = rememberInfiniteTransition(label = "HudApprovalTransition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulseGlow"
    )

    Dialog(
        onDismissRequest = {
            if (!isExecuting) {
                AppSoundManager.playClick()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF070B14))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF06B6D4).copy(alpha = 0.8f),
                            Color(0xFF10B981).copy(alpha = 0.5f),
                            Color(0xFFF59E0B).copy(alpha = 0.6f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .drawBehind {
                    // خلفية الشبكة السيبرانية الفضائية
                    val step = 36.dp.toPx()
                    val gridColor = Color(0xFF06B6D4).copy(alpha = 0.04f)
                    var x = 0f
                    while (x < size.width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += step
                    }
                }
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ══════════════════════════════════════════════════════════
                // 1. ترويسة HUD الفضائية (Spatial Header with Live Status)
                // ══════════════════════════════════════════════════════════
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF06B6D4).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = Color(0xFF06B6D4),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "AI_AUTONOMOUS_GATE // تفويض الإصلاح",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    "إشراف هندسي • موافقة صريحة مطلوبة قبل التنفيذ",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // إغلاق النافذة
                        IconButton(
                            onClick = {
                                if (!isExecuting) {
                                    AppSoundManager.playClick()
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // شريط التبويبات الفضائي (HUD Tabs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.8f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            Triple(0, "🔍 التشخيص والهدف", Icons.Default.Troubleshoot),
                            Triple(1, "💻 الكود المقترح", Icons.Default.Code),
                            Triple(2, "🐙 إجراء GitHub", Icons.Default.Source)
                        )
                        tabs.forEach { (index, label, icon) ->
                            val isSelected = selectedTab == index
                            Surface(
                                color = if (isSelected) Color(0xFF06B6D4).copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.6f)) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        AppSoundManager.playToggle()
                                        selectedTab = index
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF06B6D4) else Color(0xFF64748B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        label,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontFamily = CairoFont,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ══════════════════════════════════════════════════════════
                // 2. المحتوى التفاعلي بحسب التبويب (HUD Body)
                // ══════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // تبويب 0: تشخيص المشكلة
                            GlassmorphicSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = Color(0xFF0F172A).copy(alpha = 0.75f),
                                borderColor = Color(0xFF06B6D4).copy(alpha = 0.35f),
                                borderWidth = 1.dp
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.BugReport, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("البلاغ الأصلي:", color = GoldPrimary, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        proposal.originalReport.ifBlank { proposal.issueTitle },
                                        color = Color.White,
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            // تشخيص الذكاء الاصطناعي
                            GlassmorphicSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = Color(0xFF0F172A).copy(alpha = 0.75f),
                                borderColor = Color(0xFF10B981).copy(alpha = 0.45f),
                                borderWidth = 1.dp
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "تشخيص مهندس الذكاء الاصطناعي (Gemini Core):",
                                            color = Color(0xFF10B981),
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        proposal.aiDiagnosis,
                                        color = Color(0xFFE2E8F0),
                                        fontFamily = CairoFont,
                                        fontSize = 11.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }

                            // الملف المستهدف والوحدة
                            Surface(
                                color = Color(0xFF070B14),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("الملف المتأثر:", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = CairoFont)
                                        Text(
                                            proposal.targetFilePath,
                                            color = Color(0xFF22D3EE),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Surface(
                                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            proposal.affectedModule,
                                            color = GoldPrimary,
                                            fontSize = 9.sp,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            // تبويب 1: الكود المقترح
                            Surface(
                                color = Color(0xFF050811),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "PROPOSED_PATCH // KOTLIN_DIFF",
                                            color = Color(0xFF06B6D4),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = {
                                                AppSoundManager.playClick()
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Qabas Patch", proposal.proposedCodeAfter))
                                                Toast.makeText(context, "تم نسخ الكود إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color(0xFF06B6D4), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        proposal.proposedCodeAfter.ifBlank { "// لم يتطلب التعديل استبدال كود مباشر، سيتم إنشاء قضية متابعة." },
                                        color = Color(0xFF86EFAC),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }

                        2 -> {
                            // تبويب 2: تفاصيل GitHub والـ Pull Request
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassmorphicSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    backgroundColor = Color(0xFF0F172A).copy(alpha = 0.8f),
                                    borderColor = Color(0xFFF59E0B).copy(alpha = 0.35f),
                                    borderWidth = 1.dp
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("نوع الإجراء المحدد:", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontFamily = CairoFont)
                                        Text(
                                            when (proposal.actionType) {
                                                AiAutonomousRemediationEngine.RemediationActionType.CREATE_PULL_REQUEST -> "🐙 فتح سحب تعديل جديد (Pull Request)"
                                                AiAutonomousRemediationEngine.RemediationActionType.CREATE_GITHUB_ISSUE -> "📌 فتح قضية جديدة لتتبع الخلل (GitHub Issue)"
                                                AiAutonomousRemediationEngine.RemediationActionType.COMMIT_FILE_FIX -> "⚡ تعديل الملف المباشر (Direct Commit)"
                                                AiAutonomousRemediationEngine.RemediationActionType.LOCAL_CONFIG_PATCH -> "💾 تطبيق الباتش على الإعدادات المحلية"
                                            },
                                            color = GoldPrimary,
                                            fontFamily = CairoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("رسالة الـ Commit:", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontFamily = CairoFont)
                                        Text(
                                            proposal.commitMessage,
                                            color = Color(0xFF22D3EE),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }

                                Surface(
                                    color = Color(0xFF070B14),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("عنوان المقترح (PR / Issue Title):", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontFamily = CairoFont)
                                        Text(proposal.pullRequestTitle, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("الوصف المرفق:", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontFamily = CairoFont)
                                        Text(proposal.pullRequestBody, color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // رسائل الخطأ أو الحالة التنفيذية إن وجدت
                    if (executionError != null) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(executionError ?: "", color = Color(0xFFFCA5A5), fontSize = 10.5.sp, fontFamily = CairoFont)
                            }
                        }
                    }

                    if (isExecuting) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF06B6D4).copy(alpha = 0.12f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF06B6D4),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                executionStatusText.ifBlank { "جارٍ التواصل مع GitHub وتنفيذ الإجراء المعتمد..." },
                                color = Color(0xFF22D3EE),
                                fontFamily = CairoFont,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ══════════════════════════════════════════════════════════
                // 3. قسم طلب الموافقة الصريحة وأزرار القرار (Consent Gate & Actions)
                // ══════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // شريط التحذير الصارم والتأكيد على الشفافية
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.10f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "بوابة الأمان: لن يتم إرسال أو تعديل أي شيء على مستودعك دون موافقتك الصريحة.",
                                color = GoldPrimary,
                                fontFamily = CairoFont,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // زر الرفض والإلغاء
                        OutlinedButton(
                            onClick = {
                                AppSoundManager.playClick()
                                proposal.status = AiAutonomousRemediationEngine.ApprovalStatus.REJECTED
                                onDismiss()
                            },
                            enabled = !isExecuting,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("رفض الإجراء 🛑", fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }

                        // زر الموافقة والتفويض الفوري
                        Button(
                            onClick = {
                                AppSoundManager.playSuccess()
                                isExecuting = true
                                executionError = null
                                proposal.status = AiAutonomousRemediationEngine.ApprovalStatus.APPROVED

                                scope.launch {
                                    val result = AiAutonomousRemediationEngine.executeApprovedRemediation(
                                        context = context,
                                        proposal = proposal,
                                        onProgress = { status -> executionStatusText = status }
                                    )

                                    isExecuting = false
                                    if (result.isSuccess) {
                                        AppSoundManager.playActivate()
                                        val output = result.getOrNull() ?: "تم التنفيذ بنجاح!"
                                        Toast.makeText(context, output, Toast.LENGTH_LONG).show()
                                        onApprovedAndExecuted(output)
                                    } else {
                                        executionError = result.exceptionOrNull()?.message ?: "حدث خطأ غير متوقع أثناء التنفيذ"
                                    }
                                }
                            },
                            enabled = !isExecuting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = DeepSlate
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(44.dp)
                                .testTag("ai_remediation_approve_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "موافقة وتفويض التنفيذ 🚀",
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
