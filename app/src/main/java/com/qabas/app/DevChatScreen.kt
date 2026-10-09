package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * 🛸 DevChatScreen — شاشة المراسلة الكوانتية التفاعلية الحية بين المستخدم والمطور
 * بتصميم: Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 * مزودة بضوابط صارمة، ووسم الحالات الطارئة، ورسائل الشكر والدعاء الصادقة للمساهمين.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevChatScreen(
    userEmail: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // هوية المستخدم الحالية والصلاحيات
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }
    val isAdmin = remember { prefs.getBoolean("is_admin", false) || prefs.getBoolean("is_developer", false) }
    val effectiveEmail = remember(userEmail) {
        if (userEmail.isNotBlank()) userEmail else prefs.getString("user_email", "user@qabas.app") ?: "user@qabas.app"
    }

    // حالة الرسائل والتفاعل والضوابط الصارمة
    var messages by remember { mutableStateOf<List<DevChatRepository.DevChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isDevTyping by remember { mutableStateOf(false) }
    var senderMode by remember { mutableStateOf("user") } // "user" or "developer"
    var isUrgentMode by remember { mutableStateOf(false) } // وضع البلاغ الطارئ العاجل 🚨
    var showGuidelinesDialog by remember { mutableStateOf(false) } // نافذة الضوابط الصارمة 📜
    var showTelemetryDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) } // نافذة شارة الإرشادات ودليل الربط 🛰️
    var showSettingsDialog by remember { mutableStateOf(false) } // نافذة إعدادات القسم وخزانة GitHub ⚙️
    var showBuildLabDialog by remember { mutableStateOf(false) } // مختبر فحص سلامة البناء ونسخ المستودع 🧪
    var showQuickChips by remember { mutableStateOf(true) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var activeAudioPlayingId by remember { mutableStateOf<String?>(null) }
    var lastSendTimestamp by remember { mutableLongStateOf(0L) }
    var activeRemediationProposal by remember { mutableStateOf<AiAutonomousRemediationEngine.AiRemediationProposal?>(null) }
    var isAnalyzingProposal by remember { mutableStateOf(false) }

    // تيليمتري حي: البينغ وحالة الرادار الكوانتي
    var simulatedPingMs by remember { mutableIntStateOf(28) }
    val infiniteTransition = rememberInfiniteTransition(label = "HudInfiniteTransition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulseGlow"
    )
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "radarAngle"
    )

    // إدارة تسجيل الصوت التفاعلي
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingDurationSec = 0
            while (isRecordingAudio) {
                delay(1000)
                recordingDurationSec++
            }
        }
    }

    // إدارة محاكاة تشغيل الصوت
    LaunchedEffect(activeAudioPlayingId) {
        if (activeAudioPlayingId != null) {
            delay(4000)
            activeAudioPlayingId = null
        }
    }

    // تحميل الرسائل والمزامنة الحية
    LaunchedEffect(effectiveEmail) {
        messages = DevChatRepository.getLocalMessages(context, effectiveEmail)
        // محاكاة تقلب سرعة البينغ الحية
        launch {
            while (isActive) {
                delay(3500)
                simulatedPingMs = (24..45).random()
                // مزامنة دورية خفيفة
                val synced = DevChatRepository.syncMessages(context, effectiveEmail)
                if (synced.size != messages.size) {
                    messages = synced
                }
            }
        }
    }

    // تحريك القائمة لآخر رسالة عند التحديث
    LaunchedEffect(messages.size, isDevTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // دعم زر الرجوع الافتراضي
    BackHandler {
        AppSoundManager.playClick()
        onBack()
    }

    // إرسال رسالة نصية مع ضوابط صارمة وتحقق لمنع السبام
    fun doSendMessage(
        textToSend: String,
        category: String = "general",
        isTelemetry: Boolean = false,
        isUrgent: Boolean = isUrgentMode,
        audioLengthSec: Int = 0
    ) {
        val clean = textToSend.trim()
        if (clean.isBlank()) {
            Toast.makeText(context, "يرجى كتابة نص الرسالة قبل الإرسال ✍️", Toast.LENGTH_SHORT).show()
            return
        }

        if (clean.length < 3 && !isTelemetry && audioLengthSec == 0) {
            Toast.makeText(context, "الرسالة قصيرة جداً، يرجى كتابة تفاصيل واضحة ومفيدة 🌿", Toast.LENGTH_SHORT).show()
            return
        }

        // ضابط الحماية من التكرار السريع (Anti-Spam Rate Limiting Guard)
        val now = System.currentTimeMillis()
        if (now - lastSendTimestamp < 2000L && senderMode == "user") {
            Toast.makeText(context, "يرجى الانتظار لحظة بين الرسائل للحفاظ على نظام الإرسال ⏳", Toast.LENGTH_SHORT).show()
            return
        }
        lastSendTimestamp = now

        AppSoundManager.playSuccess()
        isSending = true

        val currentSender = senderMode
        val effectiveCategory = if (isUrgent) "urgent" else category

        scope.launch {
            val newMsg = DevChatRepository.sendMessage(
                context = context,
                userEmail = effectiveEmail,
                text = clean,
                sender = currentSender,
                category = effectiveCategory,
                isTelemetry = isTelemetry,
                isUrgent = isUrgent,
                audioLengthSec = audioLengthSec
            )
            messages = DevChatRepository.getLocalMessages(context, effectiveEmail)
            inputText = ""
            isSending = false
            if (isUrgent) {
                isUrgentMode = false
            }

            // إذا كان المرسل مستخدماً، نحفز الرد المباشر المخصص للحالات الطارئة أو رسائل الشكر والدعاء
            if (currentSender == "user") {
                DevChatRepository.simulateSmartDeveloperReply(
                    context = context,
                    userEmail = effectiveEmail,
                    userMessage = clean,
                    isUrgent = isUrgent,
                    onTyping = { typing -> isDevTyping = typing },
                    onReplyReceived = {
                        AppSoundManager.playActivate()
                        messages = DevChatRepository.getLocalMessages(context, effectiveEmail)
                    }
                )
            }
        }
    }

    // السطح السيبراني الفضائي العام
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .drawBehind {
                // رسم شبكة القيادة السيبرانية الدقيقة (Spatial Cyber Mesh)
                val gridSpacing = 48.dp.toPx()
                val gridColor = Color(0xFF06B6D4).copy(alpha = 0.035f)
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += gridSpacing
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += gridSpacing
                }

                // هالة توهج علوية كوانتية (Cyan / Emerald Radial Glow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF06B6D4).copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.8f, 60.dp.toPx()),
                        radius = 260.dp.toPx()
                    )
                )

                // هالة توهج ذهبية سفلية (Gold Primary Accent Glow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height - 120.dp.toPx()),
                        radius = 300.dp.toPx()
                    )
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ══════════════════════════════════════════════════════════
            // 1. شريط القيادة الفضائي العلوي (Spatial HUD Header)
            // ══════════════════════════════════════════════════════════
            SpatialChatHeader(
                isAdmin = isAdmin,
                senderMode = senderMode,
                simulatedPingMs = simulatedPingMs,
                pulseGlow = pulseGlow,
                radarAngle = radarAngle,
                onBack = {
                    AppSoundManager.playClick()
                    onBack()
                },
                onOpenGuide = {
                    AppSoundManager.playClick()
                    showGuideDialog = true
                },
                onOpenSettings = {
                    AppSoundManager.playClick()
                    showSettingsDialog = true
                },
                onOpenBuildLab = {
                    AppSoundManager.playActivate()
                    showBuildLabDialog = true
                },
                onOpenGuidelines = {
                    AppSoundManager.playClick()
                    showGuidelinesDialog = true
                },
                onToggleSenderMode = {
                    AppSoundManager.playToggle()
                    senderMode = if (senderMode == "user") "developer" else "user"
                    Toast.makeText(
                        context,
                        if (senderMode == "developer") "تم التبديل لوضع: الرد كمهندس ومطور 🛡️" else "تم التبديل لوضع: المستخدم 👤",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onOpenTelemetry = {
                    AppSoundManager.playClick()
                    showTelemetryDialog = true
                },
                onClearChat = {
                    AppSoundManager.playClick()
                    DevChatRepository.clearMessages(context, effectiveEmail)
                    messages = DevChatRepository.getLocalMessages(context, effectiveEmail)
                    Toast.makeText(context, "تم تنظيف سجل المحادثة 🧹", Toast.LENGTH_SHORT).show()
                }
            )

            // ══════════════════════════════════════════════════════════
            // 2. شريط التيليمتري النبضي الخطي (Linear Minimalist Telemetry Ribbon)
            // ══════════════════════════════════════════════════════════
            SpatialTelemetryRibbon(
                pulseGlow = pulseGlow,
                userEmail = effectiveEmail,
                isDevTyping = isDevTyping,
                isUrgentActive = isUrgentMode
            )

            // ══════════════════════════════════════════════════════════
            // 3. منطقة الرسائل الكوانتية التفاعلية (Interactive Message Feed)
            // ══════════════════════════════════════════════════════════
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
            ) {
                // شارة الربط والإرشادات ومصفوفة الاتصال الفضائية البارزة
                item {
                    val activeRepo = RepoVaultManager.getActiveRepo(context)
                    val isGitHubLinked = (activeRepo != null && activeRepo.owner.isNotBlank() && activeRepo.repo.isNotBlank()) ||
                            (prefs.getString("build_center_owner", "").orEmpty().isNotBlank() && prefs.getString("build_center_repo", "").orEmpty().isNotBlank())
                    val isAiActive = prefs.getBoolean("dev_ai_autonomous_enabled", true)

                    SpatialIntegrationStatusHudBanner(
                        pulseGlow = pulseGlow,
                        isAiActive = isAiActive,
                        isGitHubLinked = isGitHubLinked,
                        onOpenGuide = {
                            AppSoundManager.playClick()
                            showGuideDialog = true
                        },
                        onOpenSettings = {
                            AppSoundManager.playClick()
                            showSettingsDialog = true
                        },
                        onOpenBuildLab = {
                            AppSoundManager.playActivate()
                            showBuildLabDialog = true
                        },
                        onAutoRemediateSystem = {
                            AppSoundManager.playActivate()
                            isAnalyzingProposal = true
                            scope.launch {
                                val prop = AiAutonomousRemediationEngine.analyzeAndProposeFix(
                                    context = context,
                                    title = "فحص المنظومة البرمجية الشامل",
                                    description = "طلب فحص وتوليد حل مقترح لمشاكل النظام وبلاغات المستخدمين عبر مستودع GitHub"
                                )
                                isAnalyzingProposal = false
                                activeRemediationProposal = prop
                            }
                        }
                    )
                }

                // بطاقة الترحيب والإرشادات الكوانتية وميثاق التواصل
                item {
                    SpatialHudWelcomeBanner(
                        pulseGlow = pulseGlow,
                        onOpenGuidelines = {
                            AppSoundManager.playClick()
                            showGuidelinesDialog = true
                        },
                        onAttachTelemetry = {
                            AppSoundManager.playClick()
                            val report = DevChatRepository.generateSystemDiagnostics(context)
                            doSendMessage(report, category = "telemetry", isTelemetry = true)
                        }
                    )
                }

                // عرض فقاعات الرسائل
                items(messages, key = { it.id }) { msg ->
                    SpatialMessageBubble(
                        message = msg,
                        pulseGlow = pulseGlow,
                        isPlayingAudio = activeAudioPlayingId == msg.id,
                        onPlayAudio = {
                            AppSoundManager.playToggle()
                            activeAudioPlayingId = if (activeAudioPlayingId == msg.id) null else msg.id
                        },
                        onCopy = {
                            AppSoundManager.playClick()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Qabas Message", msg.message))
                            Toast.makeText(context, "تم نسخ الرسالة إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                        },
                        onReact = { emoji ->
                            AppSoundManager.playSuccess()
                            DevChatRepository.toggleReaction(context, effectiveEmail, msg.id, emoji)
                            messages = DevChatRepository.getLocalMessages(context, effectiveEmail)
                        },
                        onAutoRemediate = {
                            AppSoundManager.playActivate()
                            isAnalyzingProposal = true
                            scope.launch {
                                val prop = AiAutonomousRemediationEngine.analyzeAndProposeFix(
                                    context = context,
                                    title = if (msg.isUrgent) "بلاغ عاجل: ${msg.message.take(40)}" else "فحص محادثة: ${msg.message.take(40)}",
                                    description = msg.message
                                )
                                isAnalyzingProposal = false
                                activeRemediationProposal = prop
                            }
                        }
                    )
                }

                // مؤشر كتابة المطور المباشر المتوهج
                if (isDevTyping) {
                    item {
                        SpatialTypingIndicatorCard(pulseGlow = pulseGlow)
                    }
                }
            }

            // ══════════════════════════════════════════════════════════
            // 4. شريط الرقاقات السريعة التفاعلية (شكر، دعاء، طوارئ، اقتراحات)
            // ══════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = showQuickChips,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SpatialQuickChipsBar(
                    isUrgent = isUrgentMode,
                    onSelectChip = { prompt, category, makeUrgent ->
                        AppSoundManager.playClick()
                        if (makeUrgent) {
                            isUrgentMode = true
                        }
                        if (category == "telemetry") {
                            val report = DevChatRepository.generateSystemDiagnostics(context)
                            doSendMessage(report, category = "telemetry", isTelemetry = true)
                        } else if (category == "guide") {
                            showGuideDialog = true
                        } else if (category == "settings") {
                            showSettingsDialog = true
                        } else if (category == "ai_remediate") {
                            isAnalyzingProposal = true
                            scope.launch {
                                val prop = AiAutonomousRemediationEngine.analyzeAndProposeFix(
                                    context = context,
                                    title = "فحص المنظومة البرمجية",
                                    description = "طلب فحص وتوليد حل مقترح لمشاكل النظام وبلاغات المستخدمين عبر مستودع GitHub"
                                )
                                isAnalyzingProposal = false
                                activeRemediationProposal = prop
                            }
                        } else {
                            inputText = prompt
                        }
                    }
                )
            }

            // ══════════════════════════════════════════════════════════
            // 5. شريط الإدخال الكوانتي الفضائي (Spatial Glass Input Hub)
            // ══════════════════════════════════════════════════════════
            SpatialChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                isSending = isSending,
                senderMode = senderMode,
                isUrgentMode = isUrgentMode,
                onToggleUrgentMode = {
                    AppSoundManager.playToggle()
                    isUrgentMode = !isUrgentMode
                    if (isUrgentMode) {
                        Toast.makeText(context, "تم تفعيل وسم البلاغ العاجل 🚨 (أولوية قصوى)", Toast.LENGTH_SHORT).show()
                    }
                },
                isRecordingAudio = isRecordingAudio,
                recordingDurationSec = recordingDurationSec,
                pulseGlow = pulseGlow,
                showQuickChips = showQuickChips,
                onToggleQuickChips = {
                    AppSoundManager.playToggle()
                    showQuickChips = !showQuickChips
                },
                onToggleRecording = {
                    AppSoundManager.playToggle()
                    if (isRecordingAudio) {
                        // إنهاء التسجيل وإرسال رسالة صوتية محاكاة
                        val dur = recordingDurationSec.coerceAtLeast(1)
                        isRecordingAudio = false
                        doSendMessage(
                            textToSend = "🎙️ رسالة صوتية مسجلة من المستخدم ($dur ثانية)",
                            category = "voice",
                            audioLengthSec = dur
                        )
                    } else {
                        isRecordingAudio = true
                    }
                },
                onCancelRecording = {
                    AppSoundManager.playClick()
                    isRecordingAudio = false
                    recordingDurationSec = 0
                },
                onAttachTelemetry = {
                    AppSoundManager.playClick()
                    val report = DevChatRepository.generateSystemDiagnostics(context)
                    doSendMessage(report, category = "telemetry", isTelemetry = true)
                },
                onSend = {
                    doSendMessage(inputText)
                }
            )
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
                            "AI_AUTONOMOUS_DIAGNOSIS...",
                            color = Color(0xFF06B6D4),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "جارٍ فحص البلاغ ومقارنته بالكود وتجهيز طلب الموافقة 🛰️",
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
                    // توثيق الإجراء المعتمد في المحادثة بشفافية وأمانة
                    val logMessage = """
                        🛡️ [إجراء معتمد من المطور]:
                        • الإجراء: ${proposal.pullRequestTitle.ifBlank { proposal.issueTitle }}
                        • الحالة: $executionResult
                        • الملف: ${proposal.targetFilePath}
                    """.trimIndent()
                    doSendMessage(logMessage, category = "dua")
                }
            )
        }

        // نافذة فحص التيليمتري المباشرة
        if (showTelemetryDialog) {
            SpatialTelemetryInspectionDialog(
                report = DevChatRepository.generateSystemDiagnostics(context),
                onDismiss = { showTelemetryDialog = false },
                onSendToChat = {
                    showTelemetryDialog = false
                    val report = DevChatRepository.generateSystemDiagnostics(context)
                    doSendMessage(report, category = "telemetry", isTelemetry = true)
                }
            )
        }

        // نافذة الضوابط الصارمة وميثاق التواصل
        if (showGuidelinesDialog) {
            SpatialStrictGuidelinesDialog(
                onDismiss = { showGuidelinesDialog = false }
            )
        }

        // نافذة شارة الإرشادات ودليل الربط الفضائي
        if (showGuideDialog) {
            SpatialDevIntegrationGuideDialog(
                onDismiss = { showGuideDialog = false },
                onOpenSettings = {
                    showGuideDialog = false
                    showSettingsDialog = true
                },
                onTestConnection = {
                    showGuideDialog = false
                    showSettingsDialog = true
                }
            )
        }

        // نافذة إعدادات قسم التطوير والذكاء الاصطناعي
        if (showSettingsDialog) {
            SpatialDevSettingsDialog(
                onDismiss = { showSettingsDialog = false },
                onOpenGuide = {
                    showSettingsDialog = false
                    showGuideDialog = true
                },
                onClearChatHistory = {
                    DevChatRepository.clearMessages(context, effectiveEmail)
                    messages = DevChatRepository.getLocalMessages(context, effectiveEmail)
                },
                onOpenBuildLab = {
                    showSettingsDialog = false
                    showBuildLabDialog = true
                }
            )
        }

        // مختبر فحص سلامة البناء وسير العمل ونسخ المستودعات
        if (showBuildLabDialog) {
            SpatialBuildTestLabDialog(
                onDismiss = { showBuildLabDialog = false },
                onOpenSettings = {
                    showBuildLabDialog = false
                    showSettingsDialog = true
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// المكونات الفرعية الفضائية (Spatial HUD Subcomponents)
// ═══════════════════════════════════════════════════════════════════

/**
 * شريط العنوان والقيادة الفضائي
 */
@Composable
private fun SpatialChatHeader(
    isAdmin: Boolean,
    senderMode: String,
    simulatedPingMs: Int,
    pulseGlow: Float,
    radarAngle: Float,
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBuildLab: () -> Unit,
    onOpenGuidelines: () -> Unit,
    onToggleSenderMode: () -> Unit,
    onOpenTelemetry: () -> Unit,
    onClearChat: () -> Unit
) {
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.85f),
        borderColor = Color(0xFF06B6D4).copy(alpha = 0.28f),
        borderWidth = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // زر الرجوع الكوانتي
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .testTag("dev_chat_back_btn")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = GoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // رادار المطور الكوانتي المتوهج
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.5.dp, Brush.sweepGradient(listOf(Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFF06B6D4))), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF06B6D4).copy(alpha = 0.15f * pulseGlow),
                        radius = size.minDimension / 2f
                    )
                }
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color(0xFF06B6D4),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // بيانات الهوية والرابط المباشر
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "DEV_NEXUS // قَبَسْ",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                    ) {
                        Text(
                            "LIVE_HUB",
                            color = Color(0xFF10B981),
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                            .shadow(4.dp, CircleShape, ambientColor = Color(0xFF10B981), spotColor = Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        "متصل • زمن الاستجابة (${simulatedPingMs}ms)",
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 10.5.sp
                    )
                }
            }

            // أزرار الضوابط، الإرشادات، الإعدادات، والتيليمتري
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // زر شارة الإرشادات ودليل الربط
                IconButton(
                    onClick = onOpenGuide,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = "دليل الربط",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // زر إعدادات القسم وخزانة GitHub
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "الإعدادات",
                        tint = Color(0xFF06B6D4),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // زر مختبر فحص البناء ونسخ المستودع
                IconButton(
                    onClick = onOpenBuildLab,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Biotech,
                        contentDescription = "مختبر البناء",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // زر ميثاق وضوابط التواصل
                IconButton(
                    onClick = onOpenGuidelines,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = "الضوابط",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(17.dp)
                    )
                }

                // زر فحص التيليمتري
                IconButton(
                    onClick = onOpenTelemetry,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Sensors,
                        contentDescription = "تيليمتري",
                        tint = Color(0xFF06B6D4),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // تبديل هوية الإرسال للإدارة
                if (isAdmin) {
                    IconButton(
                        onClick = onToggleSenderMode,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (senderMode == "developer") Color(0xFFEF4444).copy(alpha = 0.2f)
                                else Color(0xFF06B6D4).copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            if (senderMode == "developer") Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            contentDescription = "تبديل الهوية",
                            tint = if (senderMode == "developer") Color(0xFFEF4444) else Color(0xFF06B6D4),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // خيارات تنظيف السجل
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = "تنظيف",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * شريط التيليمتري الخطي (Minimalist HUD Ribbon)
 */
@Composable
private fun SpatialTelemetryRibbon(
    pulseGlow: Float,
    userEmail: String,
    isDevTyping: Boolean,
    isUrgentActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(width = 40.dp, height = 12.dp)) {
                val midY = size.height / 2f
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, midY)
                path.lineTo(size.width * 0.3f, midY)
                path.lineTo(size.width * 0.45f, midY - 5.dp.toPx() * pulseGlow)
                path.lineTo(size.width * 0.6f, midY + 5.dp.toPx() * pulseGlow)
                path.lineTo(size.width * 0.75f, midY)
                path.lineTo(size.width, midY)
                drawPath(
                    path = path,
                    color = if (isUrgentActive) Color(0xFFEF4444) else Color(0xFF06B6D4).copy(alpha = 0.8f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (isUrgentActive) "⚠️ URGENT_FLAG_ACTIVE // PRIORITY_HIGH"
                else if (isDevTyping) "DEV_CORE: PROCESSING_RESPONSE..."
                else "PROTOCOL: WSS-CYBER-SYNC // DIRECT",
                color = if (isUrgentActive) Color(0xFFEF4444) else if (isDevTyping) Color(0xFF10B981) else Color(0xFF64748B),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            userEmail.take(18) + (if (userEmail.length > 18) "…" else ""),
            color = Color(0xFF64748B),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp
        )
    }
}

/**
 * شارة الإرشادات ومصفوفة الاتصال الفضائية البارزة أعلى المحادثة
 */
@Composable
private fun SpatialIntegrationStatusHudBanner(
    pulseGlow: Float,
    isAiActive: Boolean,
    isGitHubLinked: Boolean,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBuildLab: () -> Unit,
    onAutoRemediateSystem: () -> Unit
) {
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.2.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF06B6D4).copy(alpha = 0.5f * pulseGlow + 0.3f),
                        GoldPrimary.copy(alpha = 0.4f * pulseGlow + 0.2f),
                        Color(0xFF10B981).copy(alpha = 0.5f * pulseGlow + 0.3f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .testTag("spatial_status_hud_banner"),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0xFF080D1A).copy(alpha = 0.92f),
        borderColor = Color(0xFF06B6D4).copy(alpha = 0.3f),
        borderWidth = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF06B6D4).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFF06B6D4), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "شارة الربط والتشغيل الذكي 🛰️",
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.6.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                "INTEGRATED",
                                color = Color(0xFF10B981),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        "الذكاء الاصطناعي ومستودع GitHub يعملان تحت بوابة التفويض الصريح",
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 10.5.sp
                    )
                }

                // زر الإرشادات المباشر
                Button(
                    onClick = onOpenGuide,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الإرشادات 📖", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // مصفوفة الحالات المصغرة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniStatusTag("🤖 AI Core", if (isAiActive) "نشط ✓" else "معطل", isAiActive, Modifier.weight(1f))
                MiniStatusTag("🐙 GitHub", if (isGitHubLinked) "متصل ✓" else "بحاجة تهيئة", isGitHubLinked, Modifier.weight(1.2f))
                MiniStatusTag("🛡️ البوابة", "تفويض صريح 🔒", true, Modifier.weight(1.1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // أزرار سريعة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, Color(0xFF06B6D4).copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("الإعدادات ⚙️", fontFamily = CairoFont, fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = onOpenBuildLab,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6EE7B7)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.1f).height(32.dp)
                ) {
                    Icon(Icons.Default.Biotech, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("مختبر البناء 🧪", fontFamily = CairoFont, fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = onAutoRemediateSystem,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.2f).height(32.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("إصلاح GitHub 🤖", fontFamily = CairoFont, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MiniStatusTag(label: String, status: String, isOk: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF070B14),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.6.dp, if (isOk) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(label, color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 9.sp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(status, color = if (isOk) Color(0xFF10B981) else Color(0xFFEF4444), fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
    }
}

/**
 * بطاقة الترحيب والإرشادات الكوانتية وميثاق التواصل
 */
@Composable
private fun SpatialHudWelcomeBanner(
    pulseGlow: Float,
    onOpenGuidelines: () -> Unit,
    onAttachTelemetry: () -> Unit
) {
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.70f),
        borderColor = Color(0xFFF59E0B).copy(alpha = 0.25f),
        borderWidth = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GoldPrimary.copy(alpha = 0.15f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "قناة الدعم والمساهمة المباشرة مع مهندسي قَبَسْ 🛰️",
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Text(
                        "استقبال مقترحاتكم وبلاغاتكم العاجلة؛ مساهمتك الكريمة مقدرة ومحل دعاء واعتزاز 🌿",
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenGuidelines,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ميثاق وضوابط التواصل 📜", color = GoldPrimary, fontFamily = CairoFont, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onAttachTelemetry,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF06B6D4).copy(alpha = 0.08f)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = Color(0xFF06B6D4),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        "تقرير تشخيص الجهاز ⚡",
                        color = Color(0xFF06B6D4),
                        fontFamily = CairoFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * فقاعة الرسالة الكوانتية التفاعلية مع دعم وسوم الطوارئ، الشكر والدعاء، والتسجيل الصوتي
 */
@Composable
private fun SpatialMessageBubble(
    message: DevChatRepository.DevChatMessage,
    pulseGlow: Float,
    isPlayingAudio: Boolean,
    onPlayAudio: () -> Unit,
    onCopy: () -> Unit,
    onReact: (String) -> Unit,
    onAutoRemediate: () -> Unit = {}
) {
    val isMe = message.sender == "user"
    val isTelemetry = message.isTelemetry || message.message.contains("TELEMETRY")
    val isAudio = message.audioLengthSec > 0 || message.category == "voice"
    val isUrgent = message.isUrgent || message.category == "urgent"
    val isDua = message.category == "dua"

    val bubbleBg = when {
        isUrgent -> Color(0xFF2A0F14).copy(alpha = 0.90f)
        isDua -> Color(0xFF0E2A1F).copy(alpha = 0.90f)
        isMe -> Color(0xFF1E293B).copy(alpha = 0.85f)
        else -> Color(0xFF0D2538).copy(alpha = 0.90f)
    }

    val borderColor = when {
        isUrgent -> Color(0xFFEF4444).copy(alpha = 0.65f)
        isDua -> Color(0xFF10B981).copy(alpha = 0.55f)
        isMe -> Color(0xFFF59E0B).copy(alpha = 0.35f)
        else -> Color(0xFF06B6D4).copy(alpha = 0.45f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        // بطاقة الرسالة الزجاجية
        GlassmorphicSurface(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 330.dp)
                .shadow(
                    elevation = if (isUrgent) 8.dp else if (isMe) 4.dp else 6.dp,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    ),
                    ambientColor = if (isUrgent) Color(0xFFEF4444).copy(alpha = 0.3f) else if (isMe) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF06B6D4).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            backgroundColor = bubbleBg,
            borderColor = borderColor,
            borderWidth = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // ترويسة الرسالة مع شارة الهوية ووسم الطوارئ / الدعاء
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = when {
                                isUrgent -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                isDua -> Color(0xFF10B981).copy(alpha = 0.25f)
                                isMe -> Color(0xFFF59E0B).copy(alpha = 0.18f)
                                else -> Color(0xFF06B6D4).copy(alpha = 0.20f)
                            },
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(
                                0.6.dp,
                                when {
                                    isUrgent -> Color(0xFFEF4444).copy(alpha = 0.7f)
                                    isDua -> Color(0xFF10B981).copy(alpha = 0.7f)
                                    isMe -> Color(0xFFF59E0B).copy(alpha = 0.5f)
                                    else -> Color(0xFF06B6D4).copy(alpha = 0.6f)
                                }
                            )
                        ) {
                            Text(
                                when {
                                    isUrgent -> "🚨 نداء عاجل"
                                    isDua -> "🤲 شكر ودعاء"
                                    isMe -> "USER // أنت"
                                    else -> "DEV_CORE 🛡️"
                                },
                                color = when {
                                    isUrgent -> Color(0xFFEF4444)
                                    isDua -> Color(0xFF34D399)
                                    isMe -> Color(0xFFF59E0B)
                                    else -> Color(0xFF06B6D4)
                                },
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }

                        if (!isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isDua) Color(0xFF10B981) else Color(0xFF06B6D4),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    // طابع الوقت
                    Text(
                        message.formattedTime.ifBlank { "الآن" },
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // محتوى الرسالة بحسب النوع
                when {
                    isTelemetry -> {
                        Surface(
                            color = Color(0xFF070B14).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF06B6D4).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    message.message,
                                    color = Color(0xFF22D3EE),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                    isAudio -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPlayAudio,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlayingAudio) Color(0xFF10B981) else GoldPrimary)
                            ) {
                                Icon(
                                    if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = DeepSlate,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(16) { idx ->
                                    val barHeight = if (isPlayingAudio) {
                                        (6 + (sin(idx + pulseGlow * 10f) * 8 + 8)).dp
                                    } else {
                                        (6 + (idx % 4) * 3).dp
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(
                                                if (isPlayingAudio) Color(0xFF10B981) else Color(0xFF94A3B8)
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "00:${message.audioLengthSec.coerceAtLeast(3).toString().padStart(2, '0')}",
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                    else -> {
                        Text(
                            message.message,
                            color = Color.White,
                            fontFamily = CairoFont,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // زر التحليل الآلي والتفويض المباشر للبلاغات
                if (isUrgent || message.sender == "user" || message.category == "bug" || message.category == "urgent") {
                    Surface(
                        color = Color(0xFF06B6D4).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, Color(0xFF06B6D4).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAutoRemediate() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                "تحليل آلي وتوليد حل GitHub (بوابة الموافقة 🛡️)",
                                color = Color(0xFF22D3EE),
                                fontFamily = CairoFont,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // شريط التفاعلات والإجراءات السفلية
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // رقاقات تفاعل الإيموجي إن وجدت
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        message.reactions.forEach { (emoji, count) ->
                            Surface(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.clickable { onReact(emoji) }
                            ) {
                                Text(
                                    "$emoji $count",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // أزرار سريعة: نسخ وتفاعل سريع
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "نسخ",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        IconButton(
                            onClick = { onReact("🤲") },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Text("🤲", fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = { onReact("🌿") },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Text("🌿", fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = { onReact("✨") },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Text("✨", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * مؤشر كتابة المطور المباشر (Spatial Live Typing Card)
 */
@Composable
private fun SpatialTypingIndicatorCard(pulseGlow: Float) {
    GlassmorphicSurface(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 240.dp)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.85f),
        borderColor = Color(0xFF10B981).copy(alpha = 0.5f),
        borderWidth = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "المطور يكتب رداً ودعاءً...",
                color = Color(0xFF10B981),
                fontFamily = CairoFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))

            // 3 نقاط متحركة
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) { idx ->
                    val offsetVal = sin(pulseGlow * 6f + idx * 1.5f)
                    Box(
                        modifier = Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = (0.4f + offsetVal * 0.4f).coerceIn(0.2f, 1f)))
                    )
                }
            }
        }
    }
}

/**
 * شريط الرقاقات السريعة التفاعلية (دعاء، شكر، بلاغات طارئة، أفكار)
 */
@Composable
private fun SpatialQuickChipsBar(
    isUrgent: Boolean,
    onSelectChip: (String, String, Boolean) -> Unit
) {
    val chips = listOf(
        Triple("📖 شارة ودليل الربط", "دليل الربط والتشغيل", "guide"),
        Triple("⚙️ إعدادات وخزانة GitHub", "إعدادات القسم", "settings"),
        Triple("🤲 دعاء بالتوفيق والتسديد", "نسأل الله أن يبارك في جهودكم ويجعل هذا التطبيق صدقة جارية ونفعاً للإسلام والمسلمين 🌿", "dua"),
        Triple("🌿 شكر وتقدير للمساهمة", "جزاكم الله خيراً على إتاحة هذا التطبيق وخدمة كتاب الله، بارك الله في أوقاتكم 💎", "thanks"),
        Triple("🚨 بلاغ عاجل عن محتوى مخالف", "أرجو التدخل العاجل لمراجعة هذا المحتوى المخالف للضوابط الشرعية: ", "urgent"),
        Triple("⚡ فحص آلي وتوليد حل GitHub", "طلب فحص برمجي وتوليد إصلاح مع تفويض المطور عبر مستودع GitHub", "ai_remediate"),
        Triple("💡 اقتراح ميزة نافعة", "أقترح إضافة هذه الميزة المفيدة للمستخدمين: ", "feature"),
        Triple("🐞 رصد خلل تقني", "لاحظت وجود ملاحظة تقنية تحتاج فحصاً برمجياً: ", "bug"),
        Triple("🛰️ تقرير تشخيص النظام", "إرسال تقرير تشخيصي فوري للجهاز", "telemetry")
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(chips) { (label, prompt, category) ->
            val isChipUrgent = category == "urgent"
            val isChipDua = category == "dua" || category == "thanks"
            Surface(
                color = when {
                    isChipUrgent -> Color(0xFF3B1218).copy(alpha = 0.85f)
                    isChipDua -> Color(0xFF0F2B1F).copy(alpha = 0.85f)
                    else -> Color(0xFF0F172A).copy(alpha = 0.8f)
                },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    when {
                        isChipUrgent -> Color(0xFFEF4444).copy(alpha = 0.6f)
                        isChipDua -> Color(0xFF10B981).copy(alpha = 0.5f)
                        category == "telemetry" -> Color(0xFF06B6D4).copy(alpha = 0.5f)
                        else -> Color.White.copy(alpha = 0.15f)
                    }
                ),
                modifier = Modifier.clickable { onSelectChip(prompt, category, isChipUrgent) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        color = when {
                            isChipUrgent -> Color(0xFFFCA5A5)
                            isChipDua -> Color(0xFF6EE7B7)
                            category == "telemetry" -> Color(0xFF22D3EE)
                            else -> Color(0xFFE2E8F0)
                        },
                        fontFamily = CairoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * شريط الإدخال الكوانتي الفضائي (Spatial Glass Input Hub)
 */
@Composable
private fun SpatialChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isSending: Boolean,
    senderMode: String,
    isUrgentMode: Boolean,
    onToggleUrgentMode: () -> Unit,
    isRecordingAudio: Boolean,
    recordingDurationSec: Int,
    pulseGlow: Float,
    showQuickChips: Boolean,
    onToggleQuickChips: () -> Unit,
    onToggleRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onAttachTelemetry: () -> Unit,
    onSend: () -> Unit
) {
    GlassmorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = if (isUrgentMode) Color(0xFF220A10).copy(alpha = 0.92f) else Color(0xFF0F172A).copy(alpha = 0.88f),
        borderColor = when {
            isUrgentMode -> Color(0xFFEF4444).copy(alpha = 0.7f)
            senderMode == "developer" -> Color(0xFFEF4444).copy(alpha = 0.4f)
            else -> Color(0xFF06B6D4).copy(alpha = 0.35f)
        },
        borderWidth = 1.dp
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // حالة تسجيل الصوت الحية
            if (isRecordingAudio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "جارٍ تسجيل ملاحظتك الصوتية... 00:${recordingDurationSec.toString().padStart(2, '0')}",
                            color = Color(0xFFEF4444),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Row {
                        TextButton(onClick = onCancelRecording) {
                            Text("إلغاء", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 11.sp)
                        }
                        Button(
                            onClick = onToggleRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("إرسال الصوت 🎙️", color = DeepSlate, fontFamily = CairoFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // زر تفعيل وسم البلاغ العاجل
                    IconButton(
                        onClick = onToggleUrgentMode,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isUrgentMode) Color(0xFFEF4444).copy(alpha = 0.25f) else Color.Transparent)
                    ) {
                        Icon(
                            if (isUrgentMode) Icons.Default.Warning else Icons.Default.ReportProblem,
                            contentDescription = "وسم عاجل",
                            tint = if (isUrgentMode) Color(0xFFEF4444) else Color(0xFF94A3B8),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // زر فتح/إغلاق الرقاقات السريعة
                    IconButton(
                        onClick = onToggleQuickChips,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            if (showQuickChips) Icons.Default.ExpandMore else Icons.Default.AutoAwesome,
                            contentDescription = "قوالب سريعة",
                            tint = if (showQuickChips) GoldPrimary else Color(0xFF94A3B8),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // حقل كتابة النص الكوانتي
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = {
                            Text(
                                when {
                                    isUrgentMode -> "⚠️ اكتب البلاغ العاجل (أولوية قصوى)..."
                                    senderMode == "developer" -> "كتابة رد المطور المعتمد..."
                                    else -> "اكتب رسالتك، اقتراحك، أو دعاءك للمطور..."
                                },
                                color = if (isUrgentMode) Color(0xFFFCA5A5) else Color(0xFF64748B),
                                fontSize = 12.sp,
                                fontFamily = CairoFont
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .testTag("dev_chat_input_field"),
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isUrgentMode) Color(0xFFEF4444) else if (senderMode == "developer") Color(0xFFEF4444) else GoldPrimary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFF070B14).copy(alpha = 0.5f),
                            unfocusedContainerColor = Color(0xFF070B14).copy(alpha = 0.35f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // زر الميكروفون لتسجيل صوتي فوري
                    IconButton(
                        onClick = onToggleRecording,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "تسجيل صوتي",
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // زر الإرسال المتوهج
                    val canSend = text.isNotBlank() && !isSending
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) {
                                    if (isUrgentMode) Color(0xFFEF4444)
                                    else if (senderMode == "developer") Color(0xFFEF4444)
                                    else GoldPrimary
                                } else {
                                    Color.White.copy(alpha = 0.08f)
                                }
                            )
                            .testTag("dev_chat_send_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال",
                            tint = if (canSend) DeepSlate else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * نافذة الضوابط الصارمة وميثاق التواصل والمساهمة
 */
@Composable
private fun SpatialStrictGuidelinesDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Gavel,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "ميثاق وضوابط التواصل والمساهمة 📜",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GoldPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "حرصاً على طهارة البيئة الرقمية وضمان سرعة الاستجابة، يرجى الالتزام الصارم بالضوابط التالية:",
                    color = Color(0xFF94A3B8),
                    fontFamily = CairoFont,
                    fontSize = 11.5.sp
                )

                Surface(
                    color = Color(0xFF070B14),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GuidelineItem(
                            number = "١",
                            title = "الأدب الإسلامي وصدق النقل",
                            desc = "الالتزام بحسن الخطاب، وتحري الدقة في وصف الأعطال أو الاقتراحات مع احتساب الأجر."
                        )
                        GuidelineItem(
                            number = "٢",
                            title = "حظر الإعلانات والروابط المجهولة",
                            desc = "يُمنع منعاً باتاً نشر الروابط الخارجية غير الموثوقة أو الترويج التجاري."
                        )
                        GuidelineItem(
                            number = "٣",
                            title = "قصر وسم (عاجل 🚨) على الطوارئ",
                            desc = "استخدام وسم الطوارئ مخصص حصراً للبلاغات عن محتوى مخالف أو أعطال حرجة تعيق الاستخدام."
                        )
                        GuidelineItem(
                            number = "٤",
                            title = "الامتناع عن الردود المضللة",
                            desc = "نظامنا يلتزم بالشفافية الكاملة؛ فلا نزعم حل أي مشكلة إلا بعد فحصها واعتمادها برمجياً."
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("فهمت وأوافق على الميثاق ✓", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun GuidelineItem(
    number: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(GoldPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(desc, color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp, lineHeight = 14.sp)
        }
    }
}

/**
 * نافذة فحص التيليمتري المباشرة للهاتف والنظام
 */
@Composable
private fun SpatialTelemetryInspectionDialog(
    report: String,
    onDismiss: () -> Unit,
    onSendToChat: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Sensors,
                    contentDescription = null,
                    tint = Color(0xFF06B6D4),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "فحص تيليمتري النظام المباشر 🛰️",
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column {
                Text(
                    "يقوم هذا التقرير بجمع معلومات بيئة التشغيل، الذاكرة، ومعرف العتاد لتسريع معالجة أي استفسار أو مشكلة تقنية:",
                    color = Color(0xFF94A3B8),
                    fontFamily = CairoFont,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFF070B14),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        report,
                        color = Color(0xFF22D3EE),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(10.dp),
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSendToChat,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إرفاق في المحادثة 🚀", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
            }
        }
    )
}
