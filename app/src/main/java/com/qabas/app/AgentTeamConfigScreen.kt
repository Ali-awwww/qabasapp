package com.qabas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val hasLeader = leaderCount > 0

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
                    containerColor = Color(0xFF0B141A),
                    titleContentColor = Color(0xFFD4AF37),
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color(0xFF38BDF8)
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF0B141A),
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (!hasLeader) {
                        Text(
                            "⚠️ يجب تعيين نموذج قائد واحد على الأقل!",
                            color = Color(0xFFEF4444), fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    if (leaderCount > 1) {
                        Text(
                            "⚠️ يوجد أكثر من قائد — سيُستخدم القائد الأول فقط.",
                            color = Color(0xFFF59E0B), fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    // 🔑 تحذير إذا لم يكن لدى أي عضو مفتاح صالح
                    if (team.none { keyValidationState[it.id] == true }) {
                        Text(
                            "⚠️ لا يوجد مفتاح API صالح في الشاشة الرئيسية لأي مزود!",
                            color = Color(0xFFEF4444), fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedButton(
                            onClick = onOpenKeyHub,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444)
                            )
                        ) { Text("🔑 فتح مركز المفاتيح لإضافة مفتاح", fontSize = 13.sp) }
                    }
                    Button(
                        onClick = {
                            MultiModelAgentOrchestrator.saveTeam(context, team)
                            onSave(team)
                        },
                        enabled = hasLeader && team.any { it.enabled && keyValidationState[it.id] == true } && team.count { it.role != AgentTeamRole.LEADER && it.enabled } >= 1,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37), contentColor = Color.Black)
                    ) { Text("حفظ الفريق وبدء العمل 💪", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(Color(0xFF0E1A21)).padding(padding)
        ) {
            // شرح مختصر
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16242D)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "🧠 نمط العمل: القائد + المساعدون",
                        color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "يقوم النموذج القائد بتوزيع المهمة على المساعدين حسب رتبهم، ثم يجمع مخرجاتهم في تقرير نهائي واحد.\n\nالرتبة الأصغر رقمًا تعني سلطة أعلى وأولوية في التنفيذ.",
                        color = Color(0xFFB0BEC5), fontSize = 12.sp
                    )
                }
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
                        }
                    )
                }
            }
        }
    }

    // حوار إضافة عضو جديد
    if (showAddMemberDialog) {
        AddMemberDialog(
            onDismiss = { showAddMemberDialog = false },
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
                TextButton(onClick = { showConfirmBack = false; onBack() }) { Text("نعم، رجوع", color = Color(0xFFEF4444)) }
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
    onProviderChange: (DetectedProvider, String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (member.role == AgentTeamRole.LEADER) Color(0xFF1A2E1A) else Color(0xFF16242D)
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // رأس البطاقة: الاسم + الدور + مفتاح تفعيل + زر الحذف
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (member.role == AgentTeamRole.LEADER) Color(0xFFD4AF37) else Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${member.displayName} • ${member.modelId.takeLast(25)}",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = member.enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF10B981),
                        checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.4f),
                        uncheckedThumbColor = Color(0xFFEF4444),
                        uncheckedTrackColor = Color(0xFFEF4444).copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.height(28.dp)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // الدور (Leader / Executor / Reviewer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الدور:", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                AgentTeamRole.entries.forEach { role ->
                    FilterChip(
                        selected = member.role == role,
                        onClick = { onRoleChange(role) },
                        label = { Text(role.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD4AF37).copy(alpha = 0.3f),
                            selectedLabelColor = Color(0xFFD4AF37)
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // الرتبة — ديناميكية حسب حجم الفريق (1 إلى teamSize)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الرتبة:", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Row {
                    (1..teamSize.coerceAtMost(5)).forEach { rankNum ->
                        FilterChip(
                            selected = member.rank == rankNum,
                            onClick = { onRankChange(rankNum) },
                            label = { Text("#$rankNum", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.25f),
                                selectedLabelColor = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // المزود + النموذج
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("المزود: ${member.provider.displayName}", color = Color(0xFF00E5FF), fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (hasValidKey) "مفتاح مهيأ ✅" else "لا مفتاح ❌",
                    color = if (hasValidKey) Color(0xFF10B981) else Color(0xFFEF4444),
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onAdd: (AgentTeamMember) -> Unit,
    currentLeaderCount: Int,
    teamSize: Int = 1
) {
    var selectedProvider by remember { mutableStateOf(DetectedProvider.OPENROUTER) }
    var selectedModel by remember { mutableStateOf("anthropic/claude-sonnet-4.5") }
    var selectedRole by remember { mutableStateOf(if (currentLeaderCount == 0) AgentTeamRole.LEADER else AgentTeamRole.EXECUTOR) }
    var selectedRank by remember { mutableStateOf(if (currentLeaderCount == 0) 1 else teamSize) }
    var customName by remember { mutableStateOf("") }

    val defaultModelFor: (DetectedProvider) -> String = {
        when (it) {
            DetectedProvider.OPENROUTER -> "anthropic/claude-sonnet-4.5"
            DetectedProvider.GEMINI -> "gemini-3.5-flash"
            DetectedProvider.GROQ -> "llama-3.3-70b-versatile"
            DetectedProvider.OPENAI -> "gpt-4o"
            DetectedProvider.ANTHROPIC -> "claude-3-5-sonnet-20241022"
            DetectedProvider.HUGGINGFACE -> "Qwen/Qwen2.5-Coder-32B"
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
                    label = { Text("اسم عرض (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("المزود:", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        DetectedProvider.OPENROUTER,
                        DetectedProvider.GEMINI,
                        DetectedProvider.GROQ,
                        DetectedProvider.OPENAI
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

                Text("الدور:", color = Color(0xFFB0BEC5), fontSize = 12.sp)
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

                Text("الرتبة (1 = أعلى سلطة):", color = Color(0xFFB0BEC5), fontSize = 12.sp)
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
            Button(onClick = {
                val name = customName.ifBlank {
                    when (selectedRole) {
                        AgentTeamRole.LEADER -> "القائد"
                        AgentTeamRole.EXECUTOR -> "مساعد ${System.currentTimeMillis() % 100}"
                        AgentTeamRole.REVIEWER -> "مراجع"
                    }
                }
                onAdd(
                    AgentTeamMember(
                        id = "m_${System.currentTimeMillis()}",
                        displayName = name,
                        provider = selectedProvider,
                        modelId = selectedModel,
                        role = selectedRole,
                        rank = selectedRank.coerceAtLeast(1).coerceAtMost(5)
                    )
                )
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
