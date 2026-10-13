package com.qabas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qabas.app.ui.components.GlassmorphicSurface
import com.qabas.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * ⚙️ نافذة إعدادات قسم التطوير والمحادثة الفضائية والذكاء الاصطناعي
 * Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialDevSettingsDialog(
    onDismiss: () -> Unit,
    onOpenGuide: () -> Unit,
    onClearChatHistory: () -> Unit,
    onOpenBuildLab: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("qabas_prefs", Context.MODE_PRIVATE) }

    // بيانات مستودع GitHub
    val activeRepo = remember { RepoVaultManager.getActiveRepo(context) }
    var ownerText by remember { mutableStateOf(activeRepo?.owner ?: prefs.getString("build_center_owner", "Ali-awwww") ?: "Ali-awwww") }
    var repoText by remember { mutableStateOf(activeRepo?.repo ?: prefs.getString("build_center_repo", "qabasapp") ?: "qabasapp") }
    var tokenText by remember { mutableStateOf(activeRepo?.token ?: prefs.getString("build_center_token", "") ?: "") }
    var branchText by remember { mutableStateOf(activeRepo?.defaultBranch ?: "main") }
    var showToken by remember { mutableStateOf(false) }

    // نماذج الذكاء الاصطناعي المسجلة
    var registeredModels by remember { mutableStateOf(AiModelRegistry.getRegisteredModels(context)) }
    var activeModelId by remember { mutableStateOf(AiModelRegistry.getActiveModelId(context)) }
    var isTestingModel by remember { mutableStateOf(false) }
    var modelTestResult by remember { mutableStateOf<AiModelTestResult?>(null) }

    // إضافة نموذج مخصص جديد
    var showCustomModelForm by remember { mutableStateOf(false) }
    var customModelName by remember { mutableStateOf("") }
    var customModelProvider by remember { mutableStateOf(AiProviderType.OPENROUTER) }
    var customModelIdentifier by remember { mutableStateOf("openrouter/auto") }
    var customModelApiKey by remember { mutableStateOf("") }
    var customModelEndpoint by remember { mutableStateOf("") }
    var showCustomApiKey by remember { mutableStateOf(false) }

    // حالات الفحص والاختبار
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // مفاتيح التشغيل والتفضيلات
    var isAutonomousEngineEnabled by remember { mutableStateOf(prefs.getBoolean("dev_ai_autonomous_enabled", true)) }
    var isUrgentAutoTriage by remember { mutableStateOf(prefs.getBoolean("dev_urgent_auto_triage", true)) }
    var isSoundFxEnabled by remember { mutableStateOf(prefs.getBoolean("dev_sound_fx_enabled", true)) }
    var isTelemetryAutoAttach by remember { mutableStateOf(prefs.getBoolean("dev_telemetry_auto_attach", false)) }

    // نبض الإطار المتوهج
    val infiniteTransition = rememberInfiniteTransition(label = "settings_hud_pulse")
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "settings_pulse_val"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF020617).copy(alpha = 0.9f))
                .padding(horizontal = 14.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphicSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
                    .border(
                        1.4.dp,
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF06B6D4).copy(alpha = borderPulse),
                                GoldPrimary.copy(alpha = borderPulse * 0.7f),
                                Color(0xFF06B6D4).copy(alpha = borderPulse)
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .testTag("spatial_dev_settings_dialog"),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = Color(0xFF090D1A).copy(alpha = 0.96f),
                borderColor = Color(0xFF06B6D4).copy(alpha = 0.35f),
                borderWidth = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // ═══════════════════════════════════════════════════
                    // 1. ترويسة الإعدادات والتحكم
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
                                .border(1.5.dp, Brush.sweepGradient(listOf(Color(0xFF06B6D4), GoldPrimary)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SettingsSuggest,
                                contentDescription = null,
                                tint = Color(0xFF06B6D4),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "إعدادات قسم التطوير والذكاء الاصطناعي ⚙️",
                                    color = Color.White,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF06B6D4).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF06B6D4).copy(alpha = 0.7f))
                                ) {
                                    Text(
                                        "CONFIG_HUD",
                                        color = Color(0xFF06B6D4),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Text(
                                "تهيئة مستودع GitHub، محرك المعالجة الذكي، وبوابات الأمان الصارمة",
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // ═══════════════════════════════════════════════════
                    // 2. المحتوى القابل للتمرير
                    // ═══════════════════════════════════════════════════
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // --------------------------------------------------
                        // بطاقة مصفوفة الاتصال الفضائية المباشرة (Live Status)
                        // --------------------------------------------------
                        Surface(
                            color = Color(0xFF070B14),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "مصفوفة الاتصال وحالة النظام (LIVE_MATRIX)",
                                        color = Color(0xFF22D3EE),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatusHudChip(
                                        label = "AI CORE",
                                        status = if (isAutonomousEngineEnabled) "ACTIVE" else "OFF",
                                        isOk = isAutonomousEngineEnabled,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusHudChip(
                                        label = "GITHUB VAULT",
                                        status = if (ownerText.isNotBlank() && repoText.isNotBlank()) "LINKED" else "UNLINKED",
                                        isOk = ownerText.isNotBlank() && repoText.isNotBlank(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusHudChip(
                                        label = "CONSENT GATE",
                                        status = "STRICT_ON",
                                        isOk = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // --------------------------------------------------
                        // قسم 1: إعدادات مستودع GitHub والخزانة (Repo Vault)
                        // --------------------------------------------------
                        SettingsGroupCard(
                            title = "🐙 إعدادات مستودع GitHub والخزانة",
                            subtitle = "لتمكين الذكاء الاصطناعي من قراءة الكود وتجهيز طلبات السحب بعد موافقتك"
                        ) {
                            OutlinedTextField(
                                value = ownerText,
                                onValueChange = { ownerText = it },
                                label = { Text("مالك المستودع (Owner / Org)", fontFamily = CairoFont, fontSize = 11.5.sp) },
                                placeholder = { Text("مثال: aly750834", color = Color(0xFF64748B), fontSize = 11.5.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = hudTextFieldColors(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = repoText,
                                onValueChange = { repoText = it },
                                label = { Text("اسم المستودع (Repository Name)", fontFamily = CairoFont, fontSize = 11.5.sp) },
                                placeholder = { Text("مثال: qabas-islamic-app", color = Color(0xFF64748B), fontSize = 11.5.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = hudTextFieldColors(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = tokenText,
                                onValueChange = { tokenText = it },
                                label = { Text("رمز الوصول الشخصي (GitHub Personal Access Token)", fontFamily = CairoFont, fontSize = 11.5.sp) },
                                placeholder = { Text("ghp_...", color = Color(0xFF64748B), fontSize = 11.5.sp) },
                                singleLine = true,
                                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showToken = !showToken }) {
                                        Icon(
                                            if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "إظهار/إخفاء الرمز",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = hudTextFieldColors(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = branchText,
                                onValueChange = { branchText = it },
                                label = { Text("الفرع الافتراضي (Default Branch)", fontFamily = CairoFont, fontSize = 11.5.sp) },
                                placeholder = { Text("main", color = Color(0xFF64748B), fontSize = 11.5.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = hudTextFieldColors(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // زر اختبار الاتصال والحفظ في الخزانة
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (ownerText.isBlank() || repoText.isBlank()) {
                                            Toast.makeText(context, "يرجى كتابة اسم المالك والمستودع أولاً", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isTestingConnection = true
                                        testResultStatus = null
                                        scope.launch {
                                            val client = GitHubRepoClient(owner = ownerText.trim(), repo = repoText.trim(), token = tokenText.trim())
                                            val tree = client.getTree(branch = branchText.trim(), recursive = false)
                                            isTestingConnection = false
                                            if (tree != null) {
                                                testResultStatus = Pair(true, "تم الاتصال بنجاح بالمستودع ($ownerText/$repoText) ✓")
                                                // حفظ المستودع في الخزانة
                                                val entry = RepoVaultEntry(
                                                    owner = ownerText.trim(),
                                                    repo = repoText.trim(),
                                                    token = tokenText.trim(),
                                                    defaultBranch = branchText.trim(),
                                                    customName = repoText.trim()
                                                )
                                                RepoVaultManager.addOrUpdateRepo(context, entry, setAsActive = true)
                                                prefs.edit()
                                                    .putString("build_center_owner", ownerText.trim())
                                                    .putString("build_center_repo", repoText.trim())
                                                    .putString("build_center_token", tokenText.trim())
                                                    .apply()
                                                Toast.makeText(context, "تم حفظ المستودع في الخزانة بنجاح ✓", Toast.LENGTH_SHORT).show()
                                            } else {
                                                testResultStatus = Pair(false, "تعذر الاتصال بالمستودع! تحقق من الـ Token والصلاحيات.")
                                            }
                                        }
                                    },
                                    enabled = !isTestingConnection,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                                ) {
                                    if (isTestingConnection) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("اختبار وحفظ ⚡", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = onOpenGuide,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary)
                                ) {
                                    Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("دليل الربط", fontFamily = CairoFont, fontSize = 11.sp)
                                }
                            }

                            // نتيجة فحص الاتصال
                            testResultStatus?.let { res ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = if (res.first) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.8.dp, if (res.first) Color(0xFF10B981) else Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (res.first) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (res.first) Color(0xFF10B981) else Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(res.second, color = Color.White, fontFamily = CairoFont, fontSize = 10.5.sp)
                                    }
                                }
                            }
                        }

                        // --------------------------------------------------
                        // قسم 2: نماذج ومزودات الذكاء الاصطناعي المسجلة (AI Multi-Model Hub)
                        // --------------------------------------------------
                        SettingsGroupCard(
                            title = "🤖 نماذج ومزودات الذكاء الاصطناعي المسجلة",
                            subtitle = "تسجيل واختيار نماذج OpenRouter, DeepSeek, Groq, OpenAI, Gemini أو نقاط نهاية مخصصة"
                        ) {
                            Text("النموذج النشط المستخدم في معالجة الشفرات والبلاغات:", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)

                            // قائمة النماذج للاختيار السريع
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                registeredModels.forEach { model ->
                                    val isActive = model.id == activeModelId
                                    Surface(
                                        color = if (isActive) Color(0xFF06B6D4).copy(alpha = 0.18f) else Color(0xFF0F172A),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isActive) Color(0xFF06B6D4) else Color.White.copy(alpha = 0.1f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                activeModelId = model.id
                                                AiModelRegistry.setActiveModel(context, model.id)
                                                Toast.makeText(context, "تم تفعيل النموذج: ${model.name} ✨", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(model.provider.icon, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(model.name, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                                    if (isActive) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            color = Color(0xFF10B981).copy(alpha = 0.25f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text("ACTIVE ✓", color = Color(0xFF6EE7B7), fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                        }
                                                    }
                                                }
                                                Text("${model.provider.displayName} • ${model.modelIdentifier}", color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                            }

                                            if (model.isCustom) {
                                                IconButton(
                                                    onClick = {
                                                        AiModelRegistry.deleteModel(context, model.id)
                                                        registeredModels = AiModelRegistry.getRegisteredModels(context)
                                                        activeModelId = AiModelRegistry.getActiveModelId(context)
                                                        Toast.makeText(context, "تم حذف النموذج المخصص", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // اختبار اتصال النموذج النشط
                            val curActiveModel = registeredModels.find { it.id == activeModelId } ?: registeredModels.first()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isTestingModel = true
                                        modelTestResult = null
                                        scope.launch {
                                            val res = AiModelRegistry.testModelConnection(curActiveModel)
                                            isTestingModel = false
                                            modelTestResult = res
                                        }
                                    },
                                    enabled = !isTestingModel,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                                ) {
                                    if (isTestingModel) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSlate, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.NetworkPing, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("فحص استجابة النموذج (Ping Test) ⚡", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showCustomModelForm = !showCustomModelForm },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE))
                                ) {
                                    Icon(if (showCustomModelForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (showCustomModelForm) "إلغاء" else "إضافة نموذج", fontFamily = CairoFont, fontSize = 11.sp)
                                }
                            }

                            // نتيجة فحص النموذج
                            modelTestResult?.let { res ->
                                Surface(
                                    color = if (res.isSuccessful) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.8.dp, if (res.isSuccessful) Color(0xFF10B981) else Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (res.isSuccessful) Icons.Default.CheckCircle else Icons.Default.Error,
                                                contentDescription = null,
                                                tint = if (res.isSuccessful) Color(0xFF10B981) else Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (res.isSuccessful) "الاتصال ناجح! زمن الاستجابة: ${res.latencyMs}ms ✓" else "فشل الاتصال بالنموذج!",
                                                color = Color.White,
                                                fontFamily = CairoFont,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (res.isSuccessful && res.responseText.isNotBlank()) {
                                            Text("رد النموذج: ${res.responseText}", color = Color(0xFF6EE7B7), fontFamily = CairoFont, fontSize = 10.sp)
                                        }
                                        if (!res.isSuccessful && res.errorMessage.isNotBlank()) {
                                            Text(res.errorMessage, color = Color(0xFFFCA5A5), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                        }
                                    }
                                }
                            }

                            // نموذج إضافة مزود / نموذج ذكاء اصطناعي جديد
                            AnimatedVisibility(visible = showCustomModelForm) {
                                Surface(
                                    color = Color(0xFF070B14),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("تسجيل نموذج مخصص جديد (Custom AI Model):", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                                        OutlinedTextField(
                                            value = customModelName,
                                            onValueChange = { customModelName = it },
                                            label = { Text("اسم النموذج في التطبيق", fontFamily = CairoFont, fontSize = 11.sp) },
                                            placeholder = { Text("مثال: OpenRouter DeepSeek R1", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = hudTextFieldColors(),
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        // اختيار المزود
                                        Text("نوع المزود (Provider):", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                AiProviderType.OPENROUTER,
                                                AiProviderType.DEEPSEEK,
                                                AiProviderType.GROQ,
                                                AiProviderType.OPENAI,
                                                AiProviderType.CUSTOM_OPENAI
                                            ).forEach { p ->
                                                val isP = customModelProvider == p
                                                Surface(
                                                    color = if (isP) Color(0xFF06B6D4).copy(alpha = 0.25f) else Color(0xFF0F172A),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(0.8.dp, if (isP) Color(0xFF06B6D4) else Color.White.copy(alpha = 0.1f)),
                                                    modifier = Modifier.clickable {
                                                        customModelProvider = p
                                                        customModelIdentifier = when (p) {
                                                            AiProviderType.OPENROUTER -> "openrouter/auto"
                                                            AiProviderType.DEEPSEEK -> "deepseek-chat"
                                                            AiProviderType.GROQ -> "llama-3.3-70b-versatile"
                                                            AiProviderType.OPENAI -> "gpt-4o-mini"
                                                            else -> "custom-model"
                                                        }
                                                    }
                                                ) {
                                                    Text("${p.icon} ${p.name}", color = if (isP) Color.White else Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = customModelIdentifier,
                                            onValueChange = { customModelIdentifier = it },
                                            label = { Text("معرف النموذج (Model ID)", fontFamily = CairoFont, fontSize = 11.sp) },
                                            placeholder = { Text("deepseek/deepseek-r1 أو anthropic/claude-3.7-sonnet", fontSize = 10.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = hudTextFieldColors(),
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        OutlinedTextField(
                                            value = customModelApiKey,
                                            onValueChange = { customModelApiKey = it },
                                            label = { Text("مفتاح الـ API (API Key الخاص بك)", fontFamily = CairoFont, fontSize = 11.sp) },
                                            placeholder = { Text("sk-or-v1-... أو gsk_...", fontSize = 11.sp) },
                                            singleLine = true,
                                            visualTransformation = if (showCustomApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showCustomApiKey = !showCustomApiKey }) {
                                                    Icon(if (showCustomApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = hudTextFieldColors(),
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        if (customModelProvider == AiProviderType.CUSTOM_OPENAI) {
                                            OutlinedTextField(
                                                value = customModelEndpoint,
                                                onValueChange = { customModelEndpoint = it },
                                                label = { Text("رابط نقطة النهاية (Endpoint URL)", fontFamily = CairoFont, fontSize = 11.sp) },
                                                placeholder = { Text("https://my-api-server.com/v1/chat/completions", fontSize = 10.sp) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = hudTextFieldColors(),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (customModelName.isBlank() || customModelIdentifier.isBlank()) {
                                                    Toast.makeText(context, "يرجى كتابة اسم ومعرف النموذج", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                val newModel = AiModelConfig(
                                                    name = customModelName.trim(),
                                                    provider = customModelProvider,
                                                    modelIdentifier = customModelIdentifier.trim(),
                                                    apiKey = customModelApiKey.trim(),
                                                    customEndpoint = customModelEndpoint.trim(),
                                                    isCustom = true
                                                )
                                                AiModelRegistry.saveModel(context, newModel, setAsActive = true)
                                                registeredModels = AiModelRegistry.getRegisteredModels(context)
                                                activeModelId = newModel.id
                                                showCustomModelForm = false
                                                customModelName = ""
                                                customModelApiKey = ""
                                                Toast.makeText(context, "تم تسجيل وتفعيل النموذج بنجاح ✓", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("حفظ النموذج وتفعيله فوراً 💾", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

                            SettingsToggleRow(
                                title = "تفعيل محرك التشخيص الذاتي (Remediation Core)",
                                subtitle = "تحليل البلاغات ورسائل المطور وتقديم حلول كودية مقترحة",
                                checked = isAutonomousEngineEnabled,
                                onCheckedChange = {
                                    isAutonomousEngineEnabled = it
                                    prefs.edit().putBoolean("dev_ai_autonomous_enabled", it).apply()
                                }
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

                            SettingsToggleRow(
                                title = "الفرز التلقائي للبلاغات العاجلة (Urgent Auto-Triage)",
                                subtitle = "تجهيز مقترحات المعالجة فوراً عند وسم الرسالة بـ (عاجل 🚨)",
                                checked = isUrgentAutoTriage,
                                onCheckedChange = {
                                    isUrgentAutoTriage = it
                                    prefs.edit().putBoolean("dev_urgent_auto_triage", it).apply()
                                }
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

                            // قفل أمان دائم: بوابة الموافقة
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("بوابة الموافقة الصريحة للمطور (Consent Gate)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("مُفعل إجبارياً كصمام أمان — لا يتم كتابة أي كود دون موافقتك الصريحة", color = Color(0xFF10B981), fontFamily = CairoFont, fontSize = 10.5.sp)
                                }
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF10B981))
                                ) {
                                    Text("مُقفَل بأمان 🔒", color = Color(0xFF6EE7B7), fontFamily = CairoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                }
                            }
                        }

                        // --------------------------------------------------
                        // قسم 3: مختبر فحص سلامة البناء ونسخ المستودعات (Build Lab & Repo Duplication)
                        // --------------------------------------------------
                        SettingsGroupCard(
                            title = "🧪 مختبر فحص البناء وسير العمل السحابي",
                            subtitle = "فحص سلامة كود Kotlin، تشغيل GitHub Actions CI، وتفريغ المستودعات"
                        ) {
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenBuildLab()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Icon(Icons.Default.Biotech, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فتح مختبر فحص البناء ونسخ المستودع 🧪", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // --------------------------------------------------
                        // قسم 4: المؤثرات والصوتيات الفضائية HUD
                        // --------------------------------------------------
                        SettingsGroupCard(
                            title = "🔊 المؤثرات الفضائية والصوتيات",
                            subtitle = "تخصيص تجربة التفاعل وأصوات الـ HUD"
                        ) {
                            SettingsToggleRow(
                                title = "الأصوات التفاعلية (Spatial Audio FX)",
                                subtitle = "أصوات النقر الكوانتي وتأكيد العمليات",
                                checked = isSoundFxEnabled,
                                onCheckedChange = {
                                    isSoundFxEnabled = it
                                    prefs.edit().putBoolean("dev_sound_fx_enabled", it).apply()
                                }
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

                            SettingsToggleRow(
                                title = "إرفاق التيليمتري تلقائياً مع البلاغات الحرجة",
                                subtitle = "جمع تقرير الذاكرة والعتاد ودمجه تلقائياً مع تقارير الأعطال",
                                checked = isTelemetryAutoAttach,
                                onCheckedChange = {
                                    isTelemetryAutoAttach = it
                                    prefs.edit().putBoolean("dev_telemetry_auto_attach", it).apply()
                                }
                            )
                        }

                        // --------------------------------------------------
                        // قسم 4: إجراءات الصيانة السريعة
                        // --------------------------------------------------
                        SettingsGroupCard(
                            title = "🛠️ أدوات الصيانة والتنظيف",
                            subtitle = "إدارة السجلات والذاكرة المؤقتة"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onClearChatHistory()
                                        Toast.makeText(context, "تم مسح سجل المحادثات بنجاح ✓", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مسح السجل", fontFamily = CairoFont, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val diag = DevChatRepository.generateSystemDiagnostics(context)
                                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clip.setPrimaryClip(ClipData.newPlainText("Diagnostics", diag))
                                        Toast.makeText(context, "تم نسخ تقرير التشخيص للكليب بورد 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("نسخ التشخيص", fontFamily = CairoFont, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ═══════════════════════════════════════════════════
                    // 3. زر الإغلاق والتطبيق
                    // ═══════════════════════════════════════════════════
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("حفظ وإغلاق الإعدادات ✓", color = DeepSlate, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// المكونات الفرعية لصفحة الإعدادات
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun StatusHudChip(
    label: String,
    status: String,
    isOk: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.8.dp, if (isOk) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFFEF4444).copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                status,
                color = if (isOk) Color(0xFF10B981) else Color(0xFFEF4444),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.85f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                color = Color.White,
                fontFamily = CairoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                subtitle,
                color = Color(0xFF94A3B8),
                fontFamily = CairoFont,
                fontSize = 10.5.sp
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(subtitle, color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 10.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepSlate,
                checkedTrackColor = Color(0xFF06B6D4),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}

@Composable
private fun hudTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF06B6D4),
        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
        focusedLabelColor = Color(0xFF06B6D4),
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = Color(0xFF06B6D4)
    )
}
