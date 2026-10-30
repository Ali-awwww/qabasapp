package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.qabas.app.ui.agent.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentChatOverlayDialog(
    onDismiss: () -> Unit,
    messages: List<AgentChatMessage>,
    isThinking: Boolean,
    isRunning: Boolean,
    isPaused: Boolean,
    onSendMessage: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    specialization: AgentSpecialization,
    onNewChat: () -> Unit = {},
    onSwitchSession: (String) -> Unit = {},
    onApprovePlan: (AgentPlanCardData) -> Unit = {}
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showSessionsMenu by remember { mutableStateOf(false) }
    val sessions by AgentChatSessionManager.sessions.collectAsState()
    val activeSessionId by AgentChatSessionManager.activeSessionId.collectAsState()

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
            color = Color(0xFF070D18),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF091528),
                                Color(0xFF050B14),
                                Color(0xFF09121F)
                            )
                        )
                    )
            ) {
                // Top Header Bar: Unified Identity + Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C192E).copy(alpha = 0.95f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Identity & Status Grouped
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(specialization.color.copy(alpha = 0.12f))
                                .border(1.dp, specialization.color.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                specialization.icon,
                                contentDescription = null,
                                tint = specialization.color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = "الوكيل الذكي",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            AgentStatusChip(
                                kind = when {
                                    isThinking || (isRunning && !isPaused) -> AgentStatusKind.RUNNING
                                    isPaused -> AgentStatusKind.NEEDS_ACTION
                                    else -> AgentStatusKind.READY
                                },
                                text = when {
                                    isThinking -> "يفكر..."
                                    isRunning && !isPaused -> "يعمل"
                                    isPaused -> "موقف"
                                    else -> "جاهز"
                                },
                                modifier = Modifier.height(20.dp)
                            )
                        }
                    }

                    // Right: Actions (New Chat, Sessions Menu, Stop/Pause/Close)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // زر جلسة جديدة
                        IconButton(
                            onClick = onNewChat,
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF0F261C))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddComment,
                                contentDescription = "محادثة جديدة",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // زر قائمة الجلسات السابقة
                        Box {
                            IconButton(
                                onClick = { showSessionsMenu = true },
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF131D2E))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "السجل",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSessionsMenu,
                                onDismissRequest = { showSessionsMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF0B1424))
                                    .border(1.dp, Color(0xFF1E2D4A), RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "جلسات المحادثة السابقة",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                                HorizontalDivider(color = Color(0xFF1E2D4A))

                                if (sessions.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("لا توجد محادثات سابقة", color = Color.Gray, fontSize = 12.sp, fontFamily = CairoFont) },
                                        onClick = { showSessionsMenu = false }
                                    )
                                } else {
                                    sessions.take(8).forEach { session ->
                                        val isCurrent = session.id == activeSessionId
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = session.title,
                                                        color = if (isCurrent) Color(0xFF10B981) else Color.White,
                                                        fontSize = 12.sp,
                                                        fontFamily = CairoFont,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "${session.messageCount} رسائل",
                                                        color = Color(0xFF64748B),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showSessionsMenu = false
                                                onSwitchSession(session.id)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.ChatBubbleOutline,
                                                    contentDescription = null,
                                                    tint = if (isCurrent) Color(0xFF10B981) else Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (isRunning) {
                            IconButton(
                                onClick = { if (isPaused) onResume() else onPause() },
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF15263F))
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Pause/Resume",
                                    tint = if (isPaused) AgentDesign.Success else AgentDesign.Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onStop,
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(AgentDesign.Danger.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = AgentDesign.Danger,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF1E293B))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Chat Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageBubble(message = msg, onApprovePlan = onApprovePlan)
                    }

                    if (isThinking || (isRunning && !isPaused)) {
                        item {
                            Surface(
                                color = Color(0xFF0F2038),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "الوكيل يفكر ويولّد التعديلات البرمجية... 🧠⚡",
                                        color = Color(0xFF7DD3FC),
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Quick Prompts Redesign
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 12.dp)
                ) {
                    val quickPrompts = listOf(
                        "أضف زر مشاركة ⚡" to Icons.Default.Share,
                        "افحص الكود 🧪" to Icons.Default.Science,
                        "صياد الأخطاء 🩺" to Icons.Default.BugReport,
                        "مزامنة GitHub 🐙" to Icons.Default.CloudSync
                    )
                    items(quickPrompts) { (prompt, icon) ->
                        Surface(
                            onClick = {
                                if (!isThinking && (!isRunning || isPaused)) {
                                    onSendMessage(prompt)
                                } else {
                                    inputText = prompt
                                }
                            },
                            color = AgentDesign.Primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(AgentDesign.RadiusSm),
                            border = BorderStroke(1.dp, AgentDesign.Primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(icon, null, tint = AgentDesign.Primary, modifier = Modifier.size(14.dp))
                                Text(
                                    text = prompt,
                                    color = AgentDesign.TextPrimary,
                                    fontFamily = CairoFont,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Input Bar
                Surface(
                    color = Color(0xFF0A1220),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "أرسل توجيهاً أو تعديلاً للوكيل...",
                                    color = Color(0xFF64748B),
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F1B2E),
                                unfocusedContainerColor = Color(0xFF0D1829),
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 100.dp),
                            maxLines = 3
                        )

                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val textToSend = inputText
                                    inputText = ""
                                    onSendMessage(textToSend)
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF059669),
                                disabledContainerColor = Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "إرسال",
                                tint = if (inputText.isNotBlank()) Color.White else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatMessageBubble(
    message: AgentChatMessage,
    onApprovePlan: (AgentPlanCardData) -> Unit = {}
) {
    val isUser = message.sender == AgentChatSender.USER
    val isSystem = message.sender == AgentChatSender.TOOL_SYSTEM

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSystem) Color(0xFF1E293B) else Color(0xFF064E3B))
                        .border(1.dp, if (isSystem) Color(0xFF64748B) else Color(0xFF10B981), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSystem) Icons.Default.Build else Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (isSystem) Color(0xFF94A3B8) else Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                color = when {
                    isUser -> Color(0xFF0F3823)
                    isSystem -> Color(0xFF131D2E)
                    else -> Color(0xFF0B192E)
                },
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                border = BorderStroke(
                    1.dp,
                    when {
                        isUser -> Color(0xFF10B981).copy(alpha = 0.6f)
                        isSystem -> Color(0xFF38BDF8).copy(alpha = 0.4f)
                        else -> Color(0xFF3B82F6).copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isUser) "أنت (المطور) 👤" else if (isSystem) "نظام الأداة 🛠️" else "الوكيل البرمجي 🤖",
                        color = if (isUser) Color(0xFFA7F3D0) else if (isSystem) Color(0xFF7DD3FC) else Color(0xFF93C5FD),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )

                    message.teamSteps.takeIf { it.isNotEmpty() }?.let { steps ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            steps.forEachIndexed { index, step ->
                                val isLast = index == steps.size - 1
                                TeamStepCard(
                                    step = step,
                                    index = index,
                                    isLast = isLast,
                                    isThinking = step.isRunning
                                )
                            }
                        }
                    }

                    message.teamReport?.leaderDiffs?.firstOrNull()?.let { diff ->
                        Spacer(modifier = Modifier.height(10.dp))
                        AgentDiffCard(
                            filePath = diff.filePath,
                            additions = diff.additions,
                            deletions = diff.deletions,
                            diffText = diff.diffText
                        )
                    }

                    message.planCard?.let { planCard ->
                        Spacer(modifier = Modifier.height(8.dp))
                        InteractivePlanCard(
                            planCard = planCard,
                            onApprovePlan = onApprovePlan
                        )
                    }

                    message.diff?.let { diff ->
                        Spacer(modifier = Modifier.height(8.dp))
                        AgentDiffCard(
                            filePath = diff.filePath,
                            additions = diff.additions,
                            deletions = diff.deletions,
                            diffText = diff.diffText
                        )
                    }

                    message.errorInfo?.let { error ->
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailedDiagnosticCard(
                            error = error,
                            onRetry = { /* logic for retry would go here if needed */ }
                        )
                    }
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF065F46))
                        .border(1.dp, Color(0xFF34D399), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * بطاقة خطة التخطيط المسبق التفاعلية (Interactive Plan Card)
 */
@Composable
internal fun InteractivePlanCard(
    planCard: AgentPlanCardData,
    onApprovePlan: (AgentPlanCardData) -> Unit
) {
    var isApproved by remember { mutableStateOf(planCard.isApproved) }

    Surface(
        color = Color(0xFF071224),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isApproved) Color(0xFF10B981) else Color(0xFFA855F7)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isApproved) Color(0xFF10B981).copy(alpha = 0.2f)
                                else Color(0xFFA855F7).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isApproved) Icons.Default.CheckCircle else Icons.Default.Psychology,
                            contentDescription = null,
                            tint = if (isApproved) Color(0xFF10B981) else Color(0xFFA855F7),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = if (isApproved) "خطة العمل المعمدة ✓" else "خطة العمل المقترحة (في انتظار الاعتماد) 📐",
                        color = if (isApproved) Color(0xFF10B981) else Color(0xFFD8B4FE),
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }

                Text(
                    text = if (isApproved) "معتمدة" else "مؤقتة",
                    color = if (isApproved) Color(0xFF10B981) else Color(0xFFF59E0B),
                    fontFamily = RobotoMonoFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = planCard.summary,
                color = Color.White,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            HorizontalDivider(color = Color(0xFF1E2D4A))

            planCard.steps.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${step.number}",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoMonoFont
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.title,
                            color = Color(0xFFE2E8F0),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = step.description,
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (!isApproved) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        isApproved = true
                        planCard.isApproved = true
                        onApprovePlan(planCard)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9333EA)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "🚀 اعتماد الخطة والبدء بالتنفيذ المباشر",
                            color = Color.White,
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

/**
 * بطاقة عرض خطوة الفريق (Team Step Card) مع تأثيرات ذهبية وحركية
 */
@Composable
internal fun TeamStepCard(
    step: TeamStepEvent,
    index: Int,
    isLast: Boolean,
    isThinking: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    
    // Golden Flash/Glow Alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Animated Visibility for Sliding effect
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(
            initialOffsetX = { -150 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        ) + fadeIn(animationSpec = tween(600)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = Color(0xFF0F1B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                if (isThinking) 2.dp else 1.dp,
                if (isThinking) Color(0xFFD4AF37).copy(alpha = glowAlpha) else Color(0xFF1E2D4A)
            ),
            // Golden flash shadow when thinking
            shadowElevation = if (isThinking) (6.dp * glowAlpha) else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Role Icon with status-dependent background
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isThinking -> Color(0xFFD4AF37).copy(alpha = 0.15f)
                                    step.isSuccess -> Color(0xFF10B981).copy(alpha = 0.15f)
                                    else -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isThinking -> Color(0xFFD4AF37).copy(alpha = glowAlpha)
                                    step.isSuccess -> Color(0xFF10B981)
                                    else -> Color(0xFFEF4444)
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isThinking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFFD4AF37),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (step.isSuccess) Icons.Default.Check else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (step.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.title,
                            color = if (isThinking) Color(0xFFD4AF37) else Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (step.toolName.isNotBlank()) {
                            Text(
                                text = "أداة: ${step.toolName}",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    // Step Index Badge
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${index + 1}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = RobotoMonoFont,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (step.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = step.description,
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 44.dp)
                    )
                }

                // "Thinking" indicator with animation
                if (isThinking) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFD4AF37).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val dotTransition = rememberInfiniteTransition(label = "dots")
                            val dotAlpha by dotTransition.animateFloat(
                                initialValue = 0.2f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "dot_alpha"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD4AF37).copy(alpha = dotAlpha))
                            )
                            
                            Text(
                                text = "جارِ التفكير والتحليل الذكي الآن... ✨",
                                color = Color(0xFFD4AF37),
                                fontFamily = CairoFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (step.outputSnippet.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF070D18),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 44.dp)
                    ) {
                        Text(
                            text = step.outputSnippet,
                            color = Color(0xFF67E8F9),
                            fontSize = 10.sp,
                            fontFamily = RobotoMonoFont,
                            modifier = Modifier.padding(10.dp),
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * كارت تشخيص تفصيلي للأخطاء (Detailed Diagnostic Card)
 */
@Composable
internal fun DetailedDiagnosticCard(
    error: AgentErrorDetails,
    onRetry: () -> Unit
) {
    Surface(
        color = Color(0xFF1A0B0B),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "تشخيص خطأ الجلب / التنفيذ 🔍",
                    color = Color(0xFFEF4444),
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // تفاصيل الخطأ
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagnosticItem(label = "سبب الخطأ", value = error.reason)
                error.responseCode?.let { DiagnosticItem(label = "رمز الاستجابة", value = "$it HTTP") }
                error.cloudStatus?.let { DiagnosticItem(label = "حالة السحابة", value = it) }
                error.connectivitySpeed?.let { DiagnosticItem(label = "سرعة الاتصال", value = it) }
                
                if (error.isNetworkError) {
                    Text(
                        text = "⚠️ يبدو أن هناك مشكلة في اتصال الشبكة المحلي أو العالمي.",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontFamily = CairoFont,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfiniteTriangleRetryButton(onClick = onRetry)
        }
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = CairoFont)
        Text(text = value, color = Color.White, fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Medium)
    }
}

/**
 * زر إعادة محاولة تفاعلي مع مثلث حالة لا نهائية
 */
@Composable
internal fun InfiniteTriangleRetryButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "triangle")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFEF4444).copy(alpha = 0.1f),
            contentColor = Color(0xFFEF4444)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    path = path,
                    color = Color(0xFFEF4444),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            
            Text(
                text = "إعادة المحاولة الآن",
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp
            )
        }
    }
}
