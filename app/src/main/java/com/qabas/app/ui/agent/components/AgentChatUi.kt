package com.qabas.app

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.theme.*
import com.qabas.app.ui.agent.*


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
    val theme = QabasTheme.current
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
            color = theme.background,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, theme.success.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                theme.cardSurface,
                                theme.background,
                                theme.cardSurface
                            )
                        )
                    )
            ) {
                // Top Header Bar: Unified Identity + Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.cardSurface.copy(alpha = 0.95f))
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
                                color = theme.textPrimary,
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
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(theme.success.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddComment,
                                contentDescription = "محادثة جديدة",
                                tint = theme.success,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // زر قائمة الجلسات السابقة
                        Box {
                            IconButton(
                                onClick = { showSessionsMenu = true },
                                modifier = Modifier.size(34.dp).clip(CircleShape).background(theme.cardSurface)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "السجل",
                                    tint = theme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSessionsMenu,
                                onDismissRequest = { showSessionsMenu = false },
                                modifier = Modifier
                                    .background(theme.cardSurface)
                                    .border(1.dp, theme.separator, RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "جلسات المحادثة السابقة",
                                    color = theme.textSecondary,
                                    fontFamily = CairoFont,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                                HorizontalDivider(color = theme.separator)

                                if (sessions.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("لا توجد محادثات سابقة", color = theme.textTertiary, fontSize = 12.sp, fontFamily = CairoFont) },
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
                                                        color = if (isCurrent) theme.success else theme.textPrimary,
                                                        fontSize = 12.sp,
                                                        fontFamily = CairoFont,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "${session.messageCount} رسائل",
                                                        color = theme.textTertiary,
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
                                                    tint = if (isCurrent) theme.success else theme.textTertiary,
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
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(theme.cardSurface)
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
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(theme.separator)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = theme.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = theme.separator)

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
                                color = theme.cardSurface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f)),
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
                                        color = theme.primary,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "الوكيل يفكر ويولّد التعديلات البرمجية... 🧠⚡",
                                        color = theme.primary,
                                        fontFamily = CairoFont,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = theme.separator)

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
                    color = theme.cardSurface,
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
                                    color = theme.textTertiary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.5.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = theme.secondarySurface,
                                unfocusedContainerColor = theme.secondarySurface,
                                focusedBorderColor = theme.success,
                                unfocusedBorderColor = theme.separator,
                                focusedTextColor = theme.textPrimary,
                                unfocusedTextColor = theme.textPrimary
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
                                containerColor = theme.success,
                                disabledContainerColor = theme.separator
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "إرسال",
                                tint = if (inputText.isNotBlank()) theme.onPrimary else theme.textTertiary,
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
    val theme = QabasTheme.current
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
                        .background(if (isSystem) theme.separator else theme.success.copy(alpha = 0.35f))
                        .border(1.dp, if (isSystem) theme.textTertiary else theme.success, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSystem) Icons.Default.Build else Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (isSystem) theme.textSecondary else theme.success,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                color = when {
                    isUser -> theme.success.copy(alpha = 0.2f)
                    isSystem -> theme.cardSurface
                    else -> theme.cardSurface
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
                        isUser -> theme.success.copy(alpha = 0.6f)
                        isSystem -> theme.primary.copy(alpha = 0.4f)
                        else -> theme.primary.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isUser) "أنت (المطور) 👤" else if (isSystem) "نظام الأداة 🛠️" else "الوكيل البرمجي 🤖",
                        color = if (isUser) theme.success else if (isSystem) theme.primary else theme.primary,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.text,
                        color = theme.textPrimary,
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
                        .background(theme.success.copy(alpha = 0.35f))
                        .border(1.dp, theme.success, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = theme.textPrimary,
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
    val theme = QabasTheme.current
    var isApproved by remember { mutableStateOf(planCard.isApproved) }

    Surface(
        color = theme.cardSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isApproved) theme.success else theme.accent
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
                                if (isApproved) theme.success.copy(alpha = 0.2f)
                                else theme.accent.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isApproved) Icons.Default.CheckCircle else Icons.Default.Psychology,
                            contentDescription = null,
                            tint = if (isApproved) theme.success else theme.accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = if (isApproved) "خطة العمل المعمدة ✓" else "خطة العمل المقترحة (في انتظار الاعتماد) 📐",
                        color = if (isApproved) theme.success else theme.accent,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }

                Text(
                    text = if (isApproved) "معتمدة" else "مؤقتة",
                    color = if (isApproved) theme.success else theme.warning,
                    fontFamily = RobotoMonoFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = planCard.summary,
                color = theme.textPrimary,
                fontFamily = CairoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            HorizontalDivider(color = theme.separator)

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
                            .background(theme.separator),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${step.number}",
                            color = theme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoMonoFont
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.title,
                            color = theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = step.description,
                            color = theme.textSecondary,
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
                        containerColor = theme.accent
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
                            tint = theme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "🚀 اعتماد الخطة والبدء بالتنفيذ المباشر",
                            color = theme.onPrimary,
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
    val theme = QabasTheme.current
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
            color = theme.secondarySurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                if (isThinking) 2.dp else 1.dp,
                if (isThinking) theme.warning.copy(alpha = glowAlpha) else theme.separator
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
                                    isThinking -> theme.warning.copy(alpha = 0.15f)
                                    step.isSuccess -> theme.success.copy(alpha = 0.15f)
                                    else -> theme.destructive.copy(alpha = 0.15f)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isThinking -> theme.warning.copy(alpha = glowAlpha)
                                    step.isSuccess -> theme.success
                                    else -> theme.destructive
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isThinking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = theme.warning,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (step.isSuccess) Icons.Default.Check else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (step.isSuccess) theme.success else theme.destructive,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.title,
                            color = if (isThinking) theme.warning else theme.textPrimary,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (step.toolName.isNotBlank()) {
                            Text(
                                text = "أداة: ${step.toolName}",
                                color = theme.primary,
                                fontSize = 10.sp,
                                fontFamily = RobotoMonoFont,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                    
                    // Step Index Badge
                    Surface(
                        color = theme.separator,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${index + 1}",
                            color = theme.textSecondary,
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
                        color = theme.textSecondary,
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
                        color = theme.warning.copy(alpha = 0.1f),
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
                                    .background(theme.warning.copy(alpha = dotAlpha))
                            )
                            
                            Text(
                                text = "جارِ التفكير والتحليل الذكي الآن... ✨",
                                color = theme.warning,
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
                        color = theme.background,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, theme.separator),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 44.dp)
                    ) {
                        Text(
                            text = step.outputSnippet,
                            color = theme.primary,
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
    val theme = QabasTheme.current
    Surface(
        color = theme.destructive.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, theme.destructive.copy(alpha = 0.6f)),
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
                        .background(theme.destructive.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = theme.destructive,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "تشخيص خطأ الجلب / التنفيذ 🔍",
                    color = theme.destructive,
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
                        color = theme.destructive,
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
    val theme = QabasTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = theme.textSecondary, fontSize = 11.sp, fontFamily = CairoFont)
        Text(text = value, color = theme.textPrimary, fontSize = 11.sp, fontFamily = CairoFont, fontWeight = FontWeight.Medium)
    }
}

/**
 * زر إعادة محاولة تفاعلي مع مثلث حالة لا نهائية
 */
@Composable
internal fun InfiniteTriangleRetryButton(onClick: () -> Unit) {
    val theme = QabasTheme.current
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
            containerColor = theme.destructive.copy(alpha = 0.1f),
            contentColor = theme.destructive
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, theme.destructive.copy(alpha = 0.4f))
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
                    color = theme.destructive,
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
