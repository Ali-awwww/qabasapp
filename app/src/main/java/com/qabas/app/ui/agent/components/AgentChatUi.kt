package com.qabas.app

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    specialization: AgentSpecialization
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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
                // Top Header Bar: Icon + Name + Status + Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C192E))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Agent Identity & Status
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(specialization.color.copy(alpha = 0.15f))
                                .border(1.dp, specialization.color.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                specialization.icon,
                                contentDescription = null,
                                tint = specialization.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "الوكيل الذكي",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            AgentStatusChip(
                                kind = if (isRunning) {
                                    if (isPaused) AgentStatusKind.NEEDS_ACTION else AgentStatusKind.RUNNING
                                } else AgentStatusKind.READY,
                                text = if (isRunning) {
                                    if (isPaused) "موقف مؤقتاً" else "يعالج المهام"
                                } else "جاهز للعمل",
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }

                    // Control Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isRunning) {
                            IconButton(
                                onClick = { if (isPaused) onResume() else onPause() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF15263F), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Pause/Resume",
                                    tint = if (isPaused) AgentDesign.Success else AgentDesign.Secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onStop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(AgentDesign.Danger.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = AgentDesign.Danger,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
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
                        ChatMessageBubble(message = msg)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val quickPrompts = listOf(
                        "أضف زر مشاركة ⚡",
                        "افحص الكود 🧪",
                        "صياد الأخطاء 🩺",
                        "مزامنة GitHub 🐙"
                    )
                    quickPrompts.forEach { prompt ->
                        Surface(
                            onClick = { inputText = prompt },
                            color = AgentDesign.Primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(AgentDesign.RadiusSm),
                            border = BorderStroke(1.dp, AgentDesign.Primary.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prompt,
                                    color = AgentDesign.Primary,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
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
internal fun ChatMessageBubble(message: AgentChatMessage) {
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

                    message.diff?.let { diff ->
                        Spacer(modifier = Modifier.height(8.dp))
                        AgentDiffCard(
                            filePath = diff.filePath,
                            additions = diff.additions,
                            deletions = diff.deletions,
                            diffText = diff.diffText
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
