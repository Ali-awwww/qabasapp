package com.qabas.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * بطاقات المهمة (Mission Cards): عرض المهمة النشطة والسجل — مفصولة عن الشاشة.
 */
/**
 * بطاقة عرض نشاط الوكيل التفاعلية المباشرة
 */
@Composable
fun LiveMissionExecutionCard(
    mission: AgentMissionSession,
    onTriggerBuild: () -> Unit,
    onApplyDiff: (FileDiff) -> Unit = {},
    onDiscardDiff: (FileDiff) -> Unit = {},
    applyingPath: String? = null,
    teamProgress: String = "",
    isTeamMode: Boolean = false,
    onVerifyBuild: (() -> Unit)? = null,
    verifyingBuild: Boolean = false
) {
    val hasRunning = mission.steps.any { it.isRunning }
    // عدّاد حي — يتحدث كل ثانية فقط أثناء التنفيذ الفعلي
    var tickNow by remember(hasRunning) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hasRunning, mission.steps.size) {
        while (hasRunning) {
            delay(1000)
            tickNow = System.currentTimeMillis()
        }
    }
    // نبضة حية للنقطة أثناء العمل
    val pulse = rememberInfiniteTransition(label = "live-pulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )
    val doneSteps = mission.steps.count { !it.isRunning && it.isSuccess }
    val totalSteps = mission.steps.size.coerceAtLeast(1)

    GlassmorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.9f),
        borderColor = mission.status.color.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(mission.status.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mission.status.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = mission.status.color,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = "#${mission.id}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User Prompt echo
            Text(
                text = "الأمر: «${mission.userPrompt}»",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFCBD5E1),
                modifier = Modifier
                    .background(Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(8.dp)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // أنميشن تنفيذ المهمة — يعكس التقدم الحقيقي أثناء العمل الفعلي فقط
            if (hasRunning) {
                AgentExecutionAnimation(
                    progress = doneSteps.toFloat() / totalSteps,
                    accent = mission.status.color,
                    phaseLabel = mission.status.label,
                    active = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // مؤشر التقدم الحي — يظهر فقط أثناء التنفيذ الفعلي
            if (hasRunning) {
                val elapsedSec = ((tickNow - mission.timestamp) / 1000).coerceAtLeast(0)
                val dots = ".".repeat(((tickNow / 500) % 4).toInt().coerceIn(0, 3))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = pulse.value))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${mission.status.label}$dots",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "⏱ ${elapsedSec}ث • خطوة ${doneSteps + 1} من $totalSteps",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (isTeamMode && teamProgress.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = teamProgress,
                                fontSize = 11.sp,
                                color = Color(0xFFD4AF37),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (doneSteps.toFloat() / totalSteps).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF1E293B),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Step List (Agent Activities)
            Text(
                text = "سجل خطوات الوكيل المنفذة (Agent Activity Stream):",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mission.steps.forEachIndexed { index, step ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            if (step.isRunning) Color(0xFF00E5FF) else if (step.isSuccess) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (step.isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color(0xFF00E5FF),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (step.isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (step.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = step.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (step.isRunning) {
                                    val stepSec = ((tickNow - step.timestamp) / 1000).coerceAtLeast(0)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "⏱ ${stepSec}ث",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF00E5FF),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (step.toolName.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.wrapContentWidth()
                                    ) {
                                        Text(
                                            text = step.toolName,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF38BDF8),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            if (step.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = step.description,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 16.sp
                                )
                            }

                            if (step.outputSnippet.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = step.outputSnippet,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF67E8F9),
                                    lineHeight = 15.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF070B14), RoundedCornerShape(4.dp))
                                        .padding(6.dp)
                                        .horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }

            // Final Report & Actions
            if (mission.finalReport.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0B1B2B),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📋 ملخص إنجاز الوكيل:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = mission.finalReport,
                            fontSize = 12.sp,
                            color = Color(0xFFF1F5F9),
                            lineHeight = 18.sp
                        )

                        if (!mission.buildTriggered) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onTriggerBuild,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("بدء تجميع حزمة الـ APK على GitHub الآن ⚡", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 🔗 بوابة البناء: بعد الدفع، يفحص الفريق نتيجة CI الحقيقية ويصلح عند الفشل
                        if (isTeamMode && mission.appliedPaths.isNotEmpty() && onVerifyBuild != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onVerifyBuild,
                                enabled = !verifyingBuild,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE5B842).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                if (verifyingBuild) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFFE5B842),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("جاري فحص البناء الفعلي...", fontSize = 12.sp, color = Color(0xFFE5B842))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = Color(0xFFE5B842),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "🛠️ افحص البناء الفعلي وأصلح عبر الفريق",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE5B842)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Proposed Diffs: معاينة الفروقات + اعتماد (دفع GitHub) أو تجاهل (تراجع)
            if (mission.proposedDiffs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "🧩 مقترحات الكود (${mission.proposedDiffs.size}) — عاين ثم اعتمد أو تجاهل:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE5B842)
                )
                Spacer(modifier = Modifier.height(6.dp))
                mission.proposedDiffs.forEach { diff ->
                    var expanded by remember(diff.filePath) { mutableStateOf(false) }
                    val isApplied = diff.filePath in mission.appliedPaths
                    val isApplying = applyingPath == diff.filePath
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0B1324),
                        border = BorderStroke(1.dp, if (isApplied) Color(0xFF10B981) else Color(0xFFE5B842).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = diff.filePath.substringAfterLast("/"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "+${diff.additions}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (expanded) diff.diffText else diff.diffText.lines().take(8).joinToString("\n"),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF070B14), RoundedCornerShape(4.dp))
                                    .padding(6.dp)
                                    .clickable { expanded = !expanded },
                                maxLines = if (expanded) Int.MAX_VALUE else 8,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isApplied) {
                                    Text(
                                        text = "تم حفظ المقترح ✅",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                } else {
                                    Button(
                                        onClick = { onApplyDiff(diff) },
                                        enabled = !isApplying,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        if (isApplying) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Text("حفظ المقترح 🔐", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onDiscardDiff(diff) },
                                        enabled = !isApplying,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Text("تجاهل (تراجع) 🗑️", fontSize = 11.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * بطاقة عرض مهمة قديمة في السجل
 */
@Composable
fun HistoricalMissionCard(mission: AgentMissionSession) {
    val dateStr = remember(mission.timestamp) {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(mission.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mission.userPrompt,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateStr,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${mission.steps.size} خطوات منجزة",
                    fontSize = 10.sp,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mission.status.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = mission.status.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
