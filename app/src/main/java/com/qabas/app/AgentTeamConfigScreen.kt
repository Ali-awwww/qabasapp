package com.qabas.app

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.qabas.app.ui.theme.*

/**
 * شاشة تكوين فريق الوكيل متعدد النماذج (Multi-Model Agent Team Config):
 * يحدد المستخدم عدد الأعضاء، يختار النموذج لكل عضو، ويحدد رتبته ودوره (قائد/مساعد/مراجع).
 * الرتبة تحدد أولوية التنفيذ والسلطة.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentTeamConfigScreen(
    onBack: () -> Unit,
    onSave: (List<AgentTeamMember>) -> Unit,
    onOpenKeyHub: () -> Unit = {}
) {
    val context = LocalContext.current
    var team by remember { mutableStateOf(MultiModelAgentOrchestrator.loadTeam(context).toMutableStateList()) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showConfirmBack by remember { mutableStateOf(false) }
    var keyValidationState by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) } // 🔑 تحقق حي من صحة المفتاح لكل عضو

    // 🔑 التحقق من صحة المفتاح لكل عضو — يعيد الحساب عند أي تعديل على القائمة (snapshotFlow)
    LaunchedEffect(Unit) {
        snapshotFlow { team.toList() }
            .collect { currentTeam ->
                val newMap = mutableMapOf<String, Boolean>()
                currentTeam.forEach { member ->
                    if (member.provider != DetectedProvider.NONE) {
                        val prefsKey = UniversalKeyBrain.prefsKeyFor(member.provider)
                        val keys = if (prefsKey.isNotBlank()) {
                            KeyPoolManager.orderedKeysBySpeed(prefsKey)
                        } else emptyList()
                        newMap[member.id] = keys.isNotEmpty()
                    }
                }
                keyValidationState = newMap
            }
    }

    val leaderCount = team.count { it.role == AgentTeamRole.LEADER && it.enabled } // 🔑 القائد المفعّل فقط
    // فحص الدستور حياً مع كل تعديل
    val validation = remember(team.toList()) { MultiModelAgentOrchestrator.validateTeam(team) }
    val theme = QabasTheme.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("👥 فريق الوكيل الذكي", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (team.any { it.enabled }) showConfirmBack = true
                        else onBack()
                    }) { Icon(Icons.Default.Close, "رجوع") }
                },
                actions = {
                    IconButton(onClick = { showAddMemberDialog = true }) {
                        Icon(Icons.Default.Add, "إضافة عضو")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.gold,
                    navigationIconContentColor = theme.textPrimary,
                    actionIconContentColor = theme.primary
                )
            )
        },
        bottomBar = {
            Surface(
                color = theme.background,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (validation.errors.isNotEmpty()) {
                        Text(
                            "⛔ الحفظ متوقف حتى تُحل مخالفات الدستور أعلاه (${validation.errors.size})",
                            color = theme.destructive, fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    // 🔑 تحذير إذا لم يكن لدى أي عضو مفتاح صالح
                    if (team.none { keyValidationState[it.id] == true }) {
                        Text(
                            "⚠️ لا يوجد مفتاح API صالح في الشاشة الرئيسية لأي مزود!",
                            color = theme.destructive, fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedButton(
                            onClick = onOpenKeyHub,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = theme.destructive
                            )
                        ) { Text("🔑 فتح مركز المفاتيح لإضافة مفتاح", fontSize = 13.sp) }
                    }
                    Button(
                        onClick = {
                            MultiModelAgentOrchestrator.saveTeam(context, team)
                            onSave(team)
                        },
                        enabled = validation.isValid && team.any { it.enabled && keyValidationState[it.id] == true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.gold, contentColor = theme.background)
                    ) { Text("حفظ الفريق وبدء العمل 💪", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(theme.background).padding(padding)
        ) {
            // شرح مختصر + قوالب جاهزة
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.cardSurface),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "🧠 نمط العمل: القائد + المساعدون",
                        color = theme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "يقوم النموذج القائد بتوزيع المهمة على المساعدين حسب رتبهم وتخصصاتهم، ثم يجمع مخرجاتهم في تقرير نهائي واحد.\n\nالرتبة الأصغر رقمًا تعني سلطة أعلى وأولوية في التنفيذ.",
                        color = theme.textSecondary, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("قوالب جاهزة بنقرة:", color = theme.gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MultiModelAgentOrchestrator.teamTemplates().forEach { (name, members) ->
                            // فحص التوافر لحظياً: المزودات الناقصة مفاتيحها
                            val missing = MultiModelAgentOrchestrator.templateMissingProviders(members)
                            FilterChip(
                                selected = false,
                                onClick = {
                                    if (missing.isNotEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "⛔ قالب $name ينقصه: ${missing.joinToString("، ")} — أدخل مفاتيحها أولاً",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        team.clear()
                                        team.addAll(members.map { it.copy() })
                                        Toast.makeText(context, "تم تطبيق قالب $name ✅ — كل النماذج متوافرة", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text((if (missing.isEmpty()) "✅ " else "⚠️ ") + name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.Transparent,
                                    labelColor = if (missing.isEmpty()) theme.success else theme.warning
                                )
                            )
                        }
                    }
                }
            }

            // 📜 دستور الفريق — فحص حي للمواد
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (validation.isValid) theme.success.copy(alpha = 0.15f) else theme.destructive.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                border = BorderStroke(
                    1.dp,
                    if (validation.isValid) theme.success.copy(alpha = 0.5f)
                    else theme.destructive.copy(alpha = 0.5f)
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (validation.isValid) "📜 الدستور: سليم ✅" else "📜 الدستور: مخالفات ⛔",
                            color = if (validation.isValid) theme.success else theme.destructive,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (!validation.isValid) {
                            TextButton(onClick = {
                                val fixed = MultiModelAgentOrchestrator.autoFixTeam(team)
                                team.clear()
                                team.addAll(fixed)
                                Toast.makeText(context, "تم الإصلاح التلقائي 🛠 (رتب + تكرار)", Toast.LENGTH_SHORT).show()
                            }) { Text("🛠 إصلاح تلقائي", fontSize = 11.sp, color = theme.primary) }
                        }
                    }
                    validation.errors.forEach {
                        Text("❌ $it", color = theme.destructive, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    validation.warnings.forEach {
                        Text("⚠️ $it", color = theme.warning, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (validation.isValid && validation.warnings.isEmpty()) {
                        Text("قائد واحد • مساعد+ • رتب فريدة • تخصيصات مميزة", color = theme.success, fontSize = 11.sp)
                    }
                }
            }

            // 🔌 زر موصلات الفريق — الفحص الحي للموصلات الأربع التي يعتمد عليها التنفيذ
            var showConnectors by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showConnectors = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.primary)
            ) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("🔌 موصلات الفريق — مفاتيح الأعضاء والمستودع والبناء", fontSize = 12.sp)
            }
            if (showConnectors) {
                TeamConnectorsDialog(
                    team = team,
                    keyValidationState = keyValidationState,
                    onOpenKeyHub = onOpenKeyHub,
                    onDismiss = { showConnectors = false }
                )
            }

            // قائمة الأعضاء
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(team) { idx, member ->
                    val hasValidKey = keyValidationState[member.id] == true
                    TeamMemberCard(
                        member = member,
                        hasValidKey = hasValidKey,
                        teamSize = team.size,
                        currentLeaderCount = leaderCount,
                        onRemove = { team.removeAt(idx) },
                        onRankChange = { newRank ->
                            team[idx] = team[idx].copy(rank = newRank)
                        },
                        onRoleChange = { newRole ->
                            // منع وجود أكثر من قائد واحد
                            if (newRole == AgentTeamRole.LEADER && leaderCount > 0) {
                                return@TeamMemberCard
                            }
                            team[idx] = team[idx].copy(role = newRole)
                        },
                        onEnabledChange = { newEnabled ->
                            team[idx] = team[idx].copy(enabled = newEnabled)
                        },
                        onProviderChange = { newProvider, newModel ->
                            team[idx] = team[idx].copy(provider = newProvider, modelId = newModel)
                        },
                        onInstructionChange = { newInstruction ->
                            team[idx] = team[idx].copy(customInstruction = newInstruction)
                        },
                        siblingInstructions = team
                            .filterIndexed { i, _ -> i != idx }
                            .map { MultiModelAgentOrchestrator.normalizeInstruction(it.customInstruction) }
                            .filter { it.isNotBlank() }
                            .toSet()
                    )
                }
            }
        }
    }

    // حوار إضافة عضو جديد
    if (showAddMemberDialog) {
        AddMemberDialog(
            onDismiss = { showAddMemberDialog = false },
            existingInstructions = team
                .map { MultiModelAgentOrchestrator.normalizeInstruction(it.customInstruction) }
                .filter { it.isNotBlank() }
                .toSet(),
            onAdd = { newMember ->
                if (team.size < MultiModelAgentOrchestrator.MAX_TEAM_SIZE) {
                    team.add(newMember)
                }
                showAddMemberDialog = false
            },
            teamSize = team.size,
            currentLeaderCount = leaderCount
        )
    }

    // تأكيد الرجوع بدون حفظ
    if (showConfirmBack) {
        AlertDialog(
            onDismissRequest = { showConfirmBack = false },
            title = { Text("رجوع بدون حفظ؟") },
            text = { Text("سيتم فقدان التعديلات غير المحفوظة على تشكيلة الفريق.") },
            confirmButton = {
                TextButton(onClick = { showConfirmBack = false; onBack() }) { Text("نعم، رجوع", color = theme.destructive) }
            },
            dismissButton = { TextButton(onClick = { showConfirmBack = false }) { Text("متابعة التعديل") } }
        )
    }
}

@Composable
private fun TeamMemberCard(
    member: AgentTeamMember,
    hasValidKey: Boolean = false,
    teamSize: Int = 1,
    currentLeaderCount: Int = 0,
    onRemove: () -> Unit,
    onRankChange: (Int) -> Unit,
    onRoleChange: (AgentTeamRole) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProviderChange: (DetectedProvider, String) -> Unit,
    onInstructionChange: (String) -> Unit,
    siblingInstructions: Set<String> = emptySet()
) {
        var visible by remember { mutableStateOf(false) }
        val theme = QabasTheme.current
    LaunchedEffect(Unit) {
        delay(member.rank * 100L) // انزلاق متدرج حسب الرتبة
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(animationSpec = tween(500)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (member.role == AgentTeamRole.LEADER) theme.success.copy(alpha = 0.15f) else theme.cardSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (member.role == AgentTeamRole.LEADER) {
                        Modifier.border(
                            BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(theme.gold, theme.warning, theme.gold)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(12.dp)
        ) {
            // إضافة وميض خفيف للخلفية إذا كان القائد
            val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
            val shimmerAlpha by infiniteTransition.animateFloat(
                initialValue = 0.05f,
                targetValue = 0.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "shimmer-alpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (member.role == AgentTeamRole.LEADER) {
                            Modifier.background(theme.gold.copy(alpha = shimmerAlpha))
                        } else Modifier
                    )
            ) {
                Column(Modifier.padding(14.dp)) {
                    // رأس البطاقة: الاسم + الدور + مفتاح تفعيل + زر الحذف
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = if (member.role == AgentTeamRole.LEADER) theme.gold else theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            member.displayName,
                            color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = member.enabled,
                            onCheckedChange = onEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = theme.success,
                                checkedTrackColor = theme.success.copy(alpha = 0.4f),
                                uncheckedThumbColor = theme.destructive,
                                uncheckedTrackColor = theme.destructive.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "حذف", tint = theme.destructive, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // الدور (Leader / Executor / Reviewer)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الدور:", color = theme.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        AgentTeamRole.entries.forEach { role ->
                            FilterChip(
                                selected = member.role == role,
                                onClick = { onRoleChange(role) },
                                label = { Text(role.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.gold.copy(alpha = 0.3f),
                                    selectedLabelColor = theme.gold
                                ),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // الرتبة — ديناميكية حسب حجم الفريق (1 إلى teamSize)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الرتبة:", color = theme.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Row {
                            (1..teamSize.coerceAtMost(5)).forEach { rankNum ->
                                FilterChip(
                                    selected = member.rank == rankNum,
                                    onClick = { onRankChange(rankNum) },
                                    label = { Text("#$rankNum", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.primary.copy(alpha = 0.25f),
                                        selectedLabelColor = theme.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // المزود + النموذج + حالة المفتاح (سطر الحالة فقط — الاسم يبقى نظيفاً)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("المزود: ${member.provider.displayName}", color = theme.primary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "النموذج: ${member.modelId.takeLast(30)}",
                            color = theme.textSecondary, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (hasValidKey) "مفتاح مهيأ ✅" else "لا مفتاح ❌",
                            color = if (hasValidKey) theme.success else theme.destructive,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, softWrap = false
                        )
                    }
                    // خانة تخصيص العضو: مهمته وأسلوبه (تُحقن في برومبته الخاص)
                    Spacer(Modifier.height(6.dp))
                    var instructionDraft by remember(member.id) { mutableStateOf(member.customInstruction) }
                    var instructionTaken by remember(member.id) { mutableStateOf(false) }
                    Text("تخصيص ${member.displayName}:", color = theme.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    // تخصيصات افتراضية قابلة للاختيار حسب الدور
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MultiModelAgentOrchestrator.rolePresets(member.role).forEach { preset ->
                            val isTaken = siblingInstructions.contains(
                                MultiModelAgentOrchestrator.normalizeInstruction(preset)
                            )
                            FilterChip(
                                selected = MultiModelAgentOrchestrator.normalizeInstruction(instructionDraft) ==
                                    MultiModelAgentOrchestrator.normalizeInstruction(preset),
                                onClick = {
                                    if (!isTaken) {
                                        instructionDraft = preset
                                        onInstructionChange(preset.trim().take(500))
                                    }
                                },
                                label = { Text(preset.take(22) + "…", fontSize = 10.sp) },
                                enabled = !isTaken,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.primary.copy(alpha = 0.3f),
                                    selectedLabelColor = theme.primary,
                                    disabledLabelColor = theme.textTertiary
                                ),
                                modifier = Modifier.padding(horizontal = 1.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = instructionDraft,
                        onValueChange = {
                            instructionDraft = it
                            val norm = MultiModelAgentOrchestrator.normalizeInstruction(it)
                            instructionTaken = norm.isNotBlank() && siblingInstructions.contains(norm)
                            if (!instructionTaken) onInstructionChange(it.trim().take(500))
                        },
                        label = { Text("تخصيص مخصص (يُمنع تكرار تخصيص عضو آخر)", fontSize = 11.sp) },
                        isError = instructionTaken,
                        supportingText = {
                            if (instructionTaken) Text(
                                "⛔ هذا التخصيص مستخدم في عضو آخر — اختر غيره للفصل والنظام",
                                fontSize = 10.sp, color = theme.destructive
                            )
                        },
                        placeholder = {
                            Text(
                                if (member.role == AgentTeamRole.LEADER) "مثال: قسّم المهام لتخطيط ثم تنفيذ ثم مراجعة..."
                                else "مثال: متخصص في واجهات Compose الداكنة، يلتزم بالعربية الفصيحة...",
                                fontSize = 10.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onAdd: (AgentTeamMember) -> Unit,
    currentLeaderCount: Int,
    teamSize: Int = 1,
    existingInstructions: Set<String> = emptySet()
) {
    var selectedProvider by remember { mutableStateOf(DetectedProvider.OPENROUTER) }
    val theme = QabasTheme.current
    var selectedModel by remember { mutableStateOf("anthropic/claude-sonnet-4.5") }
    var selectedRole by remember { mutableStateOf(if (currentLeaderCount == 0) AgentTeamRole.LEADER else AgentTeamRole.EXECUTOR) }
    var selectedRank by remember { mutableStateOf(if (currentLeaderCount == 0) 1 else teamSize) }
    var customName by remember { mutableStateOf("") }
    var customInstruction by remember { mutableStateOf("") }

    val defaultModelFor: (DetectedProvider) -> String = {
        when (it) {
            DetectedProvider.OPENROUTER -> "anthropic/claude-sonnet-4.5"
            DetectedProvider.GEMINI -> "gemini-2.5-flash"
            DetectedProvider.GROQ -> "llama-3.3-70b-versatile"
            DetectedProvider.NONE -> "local_sandbox"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➕ إضافة عضو للفريق") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("اسم عرض (اختياري — اسم فقط)") },
                    supportingText = { Text("الاسم للعرض فقط، بدون تعليمات اتصال أو تنزيل", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("المزود:", color = theme.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        DetectedProvider.OPENROUTER,
                        DetectedProvider.GEMINI,
                        DetectedProvider.GROQ
                    ).forEach { p ->
                        FilterChip(
                            selected = selectedProvider == p,
                            onClick = {
                                selectedProvider = p
                                selectedModel = defaultModelFor(p)
                            },
                            label = { Text(p.displayName.take(10), fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { selectedModel = it },
                    label = { Text("اسم النموذج (Model ID)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customInstruction,
                    onValueChange = { customInstruction = it },
                    label = { Text("تخصيص العضو: مهمته وأسلوبه (اختياري)") },
                    placeholder = { Text("مثال: متخصص في تدقيق الأكواد وإصلاح الـ Null Safety", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                    isError = MultiModelAgentOrchestrator.normalizeInstruction(customInstruction)
                        .let { it.isNotBlank() && existingInstructions.contains(it) },
                    supportingText = {
                        val taken = MultiModelAgentOrchestrator.normalizeInstruction(customInstruction)
                            .let { it.isNotBlank() && existingInstructions.contains(it) }
                        if (taken) Text(
                            "⛔ هذا التخصيص محجوز لعضو آخر",
                            fontSize = 10.sp, color = theme.destructive
                        )
                    }
                )

                Text("تخصيصات جاهزة للدور المختار:", color = theme.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MultiModelAgentOrchestrator.rolePresets(selectedRole).forEach { preset ->
                        val isTaken = existingInstructions.contains(
                            MultiModelAgentOrchestrator.normalizeInstruction(preset)
                        )
                        FilterChip(
                            selected = MultiModelAgentOrchestrator.normalizeInstruction(customInstruction) ==
                                MultiModelAgentOrchestrator.normalizeInstruction(preset),
                            onClick = { if (!isTaken) customInstruction = preset },
                            label = { Text(preset.take(20) + "…", fontSize = 10.sp) },
                            enabled = !isTaken
                        )
                    }
                }

                Text("الدور:", color = theme.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AgentTeamRole.entries.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = {
                                if (role == AgentTeamRole.LEADER && currentLeaderCount > 0) {
                                    // لا نسمح بقائد ثانٍ
                                    return@FilterChip
                                }
                                selectedRole = role
                            },
                            label = { Text(role.label, fontSize = 10.sp) }
                        )
                    }
                }

                Text("الرتبة (1 = أعلى سلطة):", color = theme.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { rankNum ->
                        FilterChip(
                            selected = selectedRank == rankNum,
                            onClick = { selectedRank = rankNum },
                            label = { Text("#$rankNum", fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            val dialogContext = LocalContext.current
            Button(onClick = {
                val norm = MultiModelAgentOrchestrator.normalizeInstruction(customInstruction)
                if (norm.isNotBlank() && existingInstructions.contains(norm)) {
                    Toast.makeText(
                        dialogContext,
                        "⛔ هذا التخصيص مستخدم في عضو آخر — اختر غيره",
                        Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }
                val name = MultiModelAgentOrchestrator.cleanDisplayName(
                    customName.ifBlank {
                        when (selectedRole) {
                            AgentTeamRole.LEADER -> "القائد"
                            AgentTeamRole.EXECUTOR -> "مساعد ${System.currentTimeMillis() % 100}"
                            AgentTeamRole.REVIEWER -> "مراجع"
                        }
                    },
                    selectedRole
                )
                onAdd(
                    AgentTeamMember(
                        id = "m_${System.currentTimeMillis()}",
                        displayName = name,
                        provider = selectedProvider,
                        modelId = selectedModel,
                        role = selectedRole,
                        rank = selectedRank.coerceAtLeast(1).coerceAtMost(5),
                        customInstruction = customInstruction.trim().take(500)
                    )
                )
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/**
 * حوار موصلات الفريق: الفحص الحي للموصلات الأربع التي يعتمد عليها تنفيذ الفريق —
 * (1) مفاتيح مزودي الأعضاء (2) المستودع النشط للدفع (3) جاهزية بناء CI (4) المحرك المحلي.
 */
@Composable
private fun TeamConnectorsDialog(
    team: List<AgentTeamMember>,
    keyValidationState: Map<String, Boolean>,
    onOpenKeyHub: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val enabled = team.filter { it.enabled }
    val repo = remember { RepoVaultManager.getActiveRepo(context) }
    val localReady = remember { OnDeviceModelManager.getActiveReadyModel(context) }
    val engineOk = remember { LlamaCppService.isEngineAvailable() }
    val theme = QabasTheme.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔌 موصلات الفريق", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1) مفاتيح الأعضاء
                Text("1) مفاتيح مزودي الأعضاء", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = theme.primary)
                if (enabled.isEmpty()) {
                    Text("لا أعضاء مفعّلين — فعّل عضواً أولاً.", fontSize = 11.sp, color = theme.textSecondary)
                }
                enabled.forEach { m ->
                    val ok = keyValidationState[m.id] == true
                    val poolSize = remember(m.id, m.provider) {
                        val pk = UniversalKeyBrain.prefsKeyFor(m.provider)
                        if (pk.isNotBlank()) KeyPoolManager.getKeys(pk).size else 0
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ok) "✅" else "❌", fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${m.displayName} • ${m.provider.displayName}" + if (poolSize > 1) " ($poolSize مفاتيح 🔑)" else "",
                            fontSize = 11.sp,
                            color = theme.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = onOpenKeyHub) {
                    Text("🔑 فتح مركز المفاتيح", fontSize = 11.sp, color = theme.gold)
                }

                // 2) المستودع
                Text("2) مستودع الدفع (GitHub)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = theme.primary)
                Text(
                    if (repo != null) "✅ ${repo.owner}/${repo.repo} • ${repo.defaultBranch.ifBlank { "main" }} — الدفع يعمل (عام أو خاص بتوكن صالح)"
                    else "❌ غير مربوط — اربطه من إعدادات الوكيل ليتعرف القائد على وجهة الدفع",
                    fontSize = 11.sp, color = theme.textPrimary
                )

                // 3) البناء
                Text("3) اختبار البناء (android-ci.yml)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = theme.primary)
                Text(
                    if (repo != null) "✅ جاهز — يُشغَّل عبر trigger_build بعد الدفع المعتمد"
                    else "❌ ينتظر ربط المستودع",
                    fontSize = 11.sp, color = theme.textPrimary
                )

                // 4) المحرك المحلي
                Text("4) المحرك المحلي (احتياطي الأوفلاين)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = theme.primary)
                Text(
                    when {
                        engineOk && localReady != null -> "✅ ${localReady.name} جاهز عبر JNI"
                        localReady != null -> "⚠️ ${localReady.name} منزّل لكن الجسر غير مبني — الفريق يعمل سحابياً فقط"
                        else -> "⚠️ لا نموذج محلي — الفريق سحابي حصراً"
                    },
                    fontSize = 11.sp, color = theme.textPrimary
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
        dismissButton = {}
    )
}
