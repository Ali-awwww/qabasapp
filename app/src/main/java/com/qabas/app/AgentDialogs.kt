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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
 * حوارات الوكيل (Agent Dialogs): المفاتيح + المستودع + المنصات — مفصولة عن الشاشة.
 */
@Composable
fun ApiKeySetupDialog(
    context: Context,
    initialTab: Int = 0,
    onKeySaved: () -> Unit = {},
    onOpenOnDeviceModels: () -> Unit = {},
    onExportMission: () -> String = { "" },
    onImportMission: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var rawKey by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val contextLocal = LocalContext.current
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<LiveHealthStatus?>(null) }
    var smartSave by remember { mutableStateOf<UniversalKeyBrain.SmartSaveResult?>(null) }
    val currentEngine = remember { UniversalKeyBrain.getActiveEngine() }
    val autoDetected = remember(rawKey) { UniversalKeyBrain.detect(rawKey) }
    // تجاوز يدوي: مفاتيح OpenCode Zen بصيغة sk- تُكتشف كـ OpenAI — اختر المزود يدوياً عند الحاجة
    var forcedProvider by remember { mutableStateOf<DetectedProvider?>(null) }
    val detectedProvider = forcedProvider ?: autoDetected

    Dialog(onDismissRequest = onDismiss) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp),
            backgroundColor = Color(0xFF0B132B).copy(alpha = 0.95f),
            borderColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Header & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (selectedTab == 0) "⚙️ إعداد وفحص مفتاح الوكيل الذكي" else "🎁 منصات مفاتيح البرمجة المجانية",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Switcher Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070B14), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedTab == 0) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.Transparent,
                        border = if (selectedTab == 0) BorderStroke(1.dp, Color(0xFF00E5FF)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 0 }
                    ) {
                        Text(
                            text = "إدخال وفحص المفتاح ⚡",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                            maxLines = 1
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedTab == 1) Color(0xFFE5B842).copy(alpha = 0.25f) else Color.Transparent,
                        border = if (selectedTab == 1) BorderStroke(1.dp, Color(0xFFE5B842)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 }
                    ) {
                        Text(
                            text = "منصات المفاتيح المجانية 🎁",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) Color(0xFFE5B842) else Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // TAB 0: KEY SETUP & LIVE PROBE
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "المحرك الحالي: ${currentEngine.first.displayName}",
                            fontSize = 11.sp,
                            color = currentEngine.first.color,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = rawKey,
                            onValueChange = {
                                rawKey = it
                                testResult = null
                                smartSave = null
                            },
                            placeholder = { Text("الصق مفتاحك (AIza... لـ Gemini أو gsk_... لـ Groq)", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF00E5FF),
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF334155)
                            )
                        )

                        if (rawKey.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "المزود المكتشف تلقائياً: ${detectedProvider.displayName}",
                                fontSize = 10.sp,
                                color = detectedProvider.color,
                                fontWeight = FontWeight.Bold
                            )
                            // مفتاح Zen بصيغة sk-؟ أجبر المزود على OpenCode
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تصحيح المزود:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                FilterChip(
                                    selected = forcedProvider == null,
                                    onClick = { forcedProvider = null; smartSave = null },
                                    label = { Text("تلقائي", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = forcedProvider == DetectedProvider.OPENCODE,
                                    onClick = { forcedProvider = DetectedProvider.OPENCODE; smartSave = null },
                                    label = { Text("OpenCode Zen", fontSize = 10.sp) }
                                )
                            }
                            val existingPool = remember(rawKey, detectedProvider) {
                                val pk = UniversalKeyBrain.prefsKeyFor(detectedProvider)
                                if (pk.isNotBlank()) KeyPoolManager.getKeys(pk).size else 0
                            }
                            if (existingPool > 0) {
                                Text(
                                    text = "لديك $existingPool مفتاح محفوظ لهذا المزود — الجديد سيُضاف احتياطياً ويُفعّل 🔑",
                                    fontSize = 10.sp,
                                    color = Color(0xFFE5B842)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Live Ping Test Button — routed to the DETECTED provider's own server
                        OutlinedButton(
                            onClick = {
                                val keyToTest = normalizePastedKey(rawKey)
                                if (keyToTest.isBlank()) {
                                    Toast.makeText(context, "الرجاء لصق المفتاح أولاً لإجراء الفحص الحي", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                isTesting = true
                                coroutineScope.launch {
                                    testResult = AgentConnectivityEngine.pingProviderLive(
                                        UniversalKeyBrain.detect(keyToTest),
                                        keyToTest,
                                        KeySource.USER_INPUT
                                    )
                                    isTesting = false
                                }
                            },
                            enabled = !isTesting && rawKey.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00E5FF), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري فحص الاتصال الحي بالسيرفر...", fontSize = 11.sp, color = Color(0xFF00E5FF))
                            } else {
                                Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00E5FF))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فحص الاتصال الحي بالسيرفر الآن ⚡", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                            }
                        }

                        // Test Result Card
                        testResult?.let { res ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (res.isConnected) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, if (res.isConnected) Color(0xFF10B981) else Color(0xFFEF4444)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (res.isConnected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (res.isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (res.isConnected) "تم التحقق من الاتصال الحي بنجاح (${res.latencyMs}ms) ✅" else "فشل الفحص الحي (${res.latencyMs}ms) ❌",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        if (res.isConnected && res.modelName.isNotBlank()) {
                                            Text(
                                                text = "النموذج/الهوية: ${res.modelName} 🤖",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF00E5FF)
                                            )
                                        }
                                        Text(
                                            text = res.statusDetail,
                                            fontSize = 10.sp,
                                            color = if (res.isConnected) Color(0xFFCBD5E1) else Color(0xFFFCA5A5)
                                        )
                                    }
                                }
                            }
                        }

                        // Smart-save rejection card — السبب الحقيقي لرفض الحفظ
                        smartSave?.let { res ->
                            val reason = when (res) {
                                is UniversalKeyBrain.SmartSaveResult.Failed -> "${res.provider.displayName}: ${res.reason}"
                                is UniversalKeyBrain.SmartSaveResult.Unsupported -> res.reason
                                else -> ""
                            }
                            if (reason.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "لم يُحفظ المفتاح ⛔ — $reason",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                TextButton(onClick = { selectedTab = 1 }) {
                                    Icon(imageVector = Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFE5B842))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تصفح المنصات المجانية 🎁", color = Color(0xFFE5B842), fontSize = 11.sp)
                                }
                                TextButton(onClick = {
                                    onDismiss()
                                    onOpenOnDeviceModels()
                                }) {
                                    Icon(imageVector = Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00E5FF))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("النماذج المحلية أوفلاين 📴", color = Color(0xFF00E5FF), fontSize = 11.sp)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onDismiss) {
                                    Text("إلغاء", color = Color(0xFF94A3B8))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (rawKey.isBlank()) {
                                            Toast.makeText(context, "الصق المفتاح أولاً", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isTesting = true
                                        smartSave = null
                                        coroutineScope.launch {
                                            // فحص ذكي ثم حفظ: التعرّف ← الاتصال الحي ← الحفظ عند النجاح فقط
                                            when (val res = UniversalKeyBrain.saveKeyIfLive(context, rawKey, forcedProvider)) {
                                                is UniversalKeyBrain.SmartSaveResult.Saved -> {
                                                    isTesting = false
                                                    val extra = if (res.modelName.isNotBlank()) " • ${res.modelName}" else ""
                                                    val poolNote = if (res.wasDuplicate) " (موجود مسبقاً — رُقّي نشطاً)" else " • المسبح: ${res.poolSize} مفاتيح"
                                                    Toast.makeText(
                                                        context,
                                                        "تم التعرّف على ${res.provider.displayName} والاتصال به (${res.latencyMs}ms)$extra — حُفظ وفُعّل ✅$poolNote",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    onKeySaved()
                                                    onDismiss()
                                                }
                                                is UniversalKeyBrain.SmartSaveResult.Failed -> {
                                                    isTesting = false
                                                    smartSave = res
                                                }
                                                is UniversalKeyBrain.SmartSaveResult.Unsupported -> {
                                                    isTesting = false
                                                    smartSave = res
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isTesting && rawKey.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF070B14))
                                ) {
                                    if (isTesting) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF070B14), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        if (isTesting) "يفحص ويحفظ..." else "فحص وحفظ تلقائي ⚡",
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: CODING PLATFORMS DIRECTORY
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        Text(
                            text = "منصات نماذج البرمجة المجانية مرتبة حسب القوة والسرعة:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE5B842)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(CodingPlatformDirectory.platforms) { platform ->
                                CodingPlatformCard(
                                    platform = platform,
                                    onOpenUrl = {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(platform.apiKeyUrl)).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        }.onFailure {
                                            Toast.makeText(context, "تعذر فتح المتصفح: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onCopyUrl = {
                                        clipboardManager.setText(AnnotatedString(platform.apiKeyUrl))
                                        Toast.makeText(context, "تم نسخ رابط ${platform.name} بنجاح 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    onGoToInput = {
                                        selectedTab = 0
                                    },
                                    onExportMission = onExportMission,
                                    onImportMission = onImportMission
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color(0xFF00E5FF))
                        ) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("العودة لإدخال واختبار المفتاح ⚡", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * بطاقة تفاعلية لعرض منصة نماذج البرمجة المجانية مع الخطوات والروابط
 */
@Composable
fun CodingPlatformCard(
    platform: CodingPlatformGuide,
    onOpenUrl: () -> Unit,
    onCopyUrl: () -> Unit,
    onGoToInput: () -> Unit,
    onExportMission: () -> String = { "" },
    onImportMission: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.9f),
        border = BorderStroke(1.dp, platform.badgeColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Rank, Name, Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = platform.badgeColor.copy(alpha = 0.2f),
                        border = BorderStroke(0.8.dp, platform.badgeColor)
                    ) {
                        Text(
                            text = "#${platform.rank}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = platform.badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = platform.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = platform.badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = platform.badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scores Row: Power & Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "قوة البرمجة: ${platform.powerScore}",
                            fontSize = 10.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFF59E0B))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "السرعة: ${platform.speedScore}",
                            fontSize = 10.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Key Models
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(platform.keyModels) { modelName ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF070B14),
                        border = BorderStroke(0.6.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = modelName,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Description
            Text(
                text = platform.description,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Free Tier Box
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF10B981).copy(alpha = 0.1f),
                border = BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = platform.freeTierInfo,
                        fontSize = 10.sp,
                        color = Color(0xFF6EE7B7),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Numbered Steps
            Text(
                text = "خطوات الحصول على المفتاح:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCBD5E1)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                platform.steps.forEachIndexed { idx, stepText ->
                    Row(verticalAlignment = Alignment.Top) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.size(14.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stepText,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mission Management Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val json = onExportMission()
                        if (json.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(json))
                            Toast.makeText(context, "تم نسخ المهمة إلى الحافظة", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "لا توجد مهمة للتصدير", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepSlate),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export Mission", fontSize = 10.sp, color = Color(0xFF00E5FF))
                }
                OutlinedButton(
                    onClick = {
                        onImportMission()
                        Toast.makeText(context, "تم استيراد المهمة من الحالة المحفوظة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DeepSlate),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("استيراد مهمة", fontSize = 10.sp, color = Color(0xFF00E5FF))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onOpenUrl,
                    colors = ButtonDefaults.buttonColors(containerColor = platform.badgeColor, contentColor = Color(0xFF070B14)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("فتح المنصة ↗️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCopyUrl,
                    border = BorderStroke(1.dp, Color(0xFF64748B)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("نسخ الرابط", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                }

                OutlinedButton(
                    onClick = onGoToInput,
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Text("لصق المفتاح", fontSize = 10.sp, color = Color(0xFF00E5FF))
                }
            }
        }
    }
}

/**
 * حوار إدارة وتبديل مستودعات GitHub والتحكم بالصلاحيات
 */
@Composable
fun RepoSetupDialog(
    context: Context,
    activeRepo: RepoVaultEntry?,
    onDismiss: () -> Unit,
    onRepoChanged: (RepoVaultEntry) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var repos by remember {
        mutableStateOf(RepoVaultManager.getRepositories(context))
    }
    var currentActiveId by remember {
        mutableStateOf(RepoVaultManager.getActiveRepoId(context) ?: activeRepo?.id ?: "")
    }
    val currentActiveRepo = remember(repos, currentActiveId) {
        repos.find { it.id == currentActiveId } ?: activeRepo ?: repos.firstOrNull()
    }

    var showAddSection by remember { mutableStateOf(false) }
    var inputRepoUrl by remember { mutableStateOf("") }
    var inputToken by remember { mutableStateOf("") }
    var inputBranch by remember { mutableStateOf("main") }
    var showTokenText by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun testConnection(entry: RepoVaultEntry) {
        if (entry.owner.isBlank() || entry.repo.isBlank()) {
            testSuccess = false
            testResult = "بيانات المستودع غير مكتملة."
            return
        }
        isTestingConnection = true
        testSuccess = null
        testResult = "جاري فحص الاتصال وقراءة بيانات المستودع..."
        scope.launch(Dispatchers.IO) {
            val client = GitHubRepoClient(entry.owner, entry.repo, entry.token)
            val branches = client.getBranches()
            val authUser = client.getAuthUser()
            withContext(Dispatchers.Main) {
                isTestingConnection = false
                if (branches != null) {
                    testSuccess = true
                    testResult = "الاتصال ناجح! (${branches.size} فروع)${if (!authUser.isNullOrBlank()) " | الحساب: $authUser" else ""}"
                } else {
                    testSuccess = false
                    testResult = if (entry.token.isBlank()) {
                        "المستودع خاص أو غير متاح. يرجى توفير رمز GitHub PAT."
                    } else {
                        "فشل الاتصال: يرجى التحقق من اسم المستودع وصلاحيات الرمز (PAT)."
                    }
                }
            }
        }
    }

    fun handleSwitchRepo(entry: RepoVaultEntry) {
        val target = RepoVaultManager.setActiveRepo(context, entry.id)
        currentActiveId = entry.id
        testResult = null
        testSuccess = null
        target?.let { onRepoChanged(it) }
        Toast.makeText(context, "تم تفعيل المستودع: ${entry.displayName} ⚡", Toast.LENGTH_SHORT).show()
    }

    fun handleAddRepo() {
        val parsed = RepoVaultManager.parseRepoUrl(inputRepoUrl)
        if (parsed == null) {
            Toast.makeText(context, "يرجى كتابة رابط المستودع أو صيغة: owner/repo", Toast.LENGTH_LONG).show()
            return
        }
        val newEntry = RepoVaultEntry(
            id = UUID.randomUUID().toString(),
            owner = parsed.first,
            repo = parsed.second,
            token = inputToken.trim(),
            defaultBranch = inputBranch.trim().ifBlank { "main" },
            customName = parsed.second
        )
        val updated = RepoVaultManager.addOrUpdateRepo(context, newEntry, setAsActive = true)
        repos = updated
        currentActiveId = newEntry.id
        onRepoChanged(newEntry)
        inputRepoUrl = ""
        inputToken = ""
        showAddSection = false
        Toast.makeText(context, "تمت إضافة المستودع وتفعيله 🚀", Toast.LENGTH_SHORT).show()
        testConnection(newEntry)
    }

    fun handleDeleteRepo(id: String) {
        val updated = RepoVaultManager.deleteRepo(context, id)
        repos = updated
        currentActiveId = RepoVaultManager.getActiveRepoId(context) ?: ""
        testResult = null
        testSuccess = null
        Toast.makeText(context, "تم حذف المستودع من القائمة 🗑️", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassmorphicSurface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            backgroundColor = Color(0xFF070D18),
            borderColor = Color(0xFF10B981).copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🐙", fontSize = 16.sp)
                        }
                        Column {
                            Text(
                                text = "مستودعات GitHub المتصلة",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = CairoFont
                            )
                            Text(
                                text = "التبديل بين المشاريع وطلب صلاحيات البناء",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: One-Click Smart GitHub Scopes Request
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0F261C),
                                        Color(0xFF091811)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("⚡", fontSize = 14.sp)
                                Text(
                                    text = "توليد مفتاح الصلاحيات بنقرة واحدة (GitHub PAT)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    fontFamily = CairoFont
                                )
                            }
                            Text(
                                text = "يفتح صفحة GitHub مع الصلاحيات المطلوبة للوكيل والبناء (repo, workflow, write:packages, read:user) محددة مسبقاً وتلقائياً دون الحاجة لتحديدها يدوياً.",
                                fontSize = 10.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 14.sp,
                                fontFamily = CairoFont
                            )
                            Button(
                                onClick = {
                                    val tokenUrl = "https://github.com/settings/tokens/new?description=Qabas+Studio+DevOps+Agent&scopes=repo,workflow,write:packages,read:user"
                                    try {
                                        uriHandler.openUri(tokenUrl)
                                        Toast.makeText(context, "جاري فتح GitHub لإنشاء الرمز بالصلاحيات 🐙", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tokenUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color(0xFF050B14)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "فتح وتوليد الصلاحيات على GitHub ↗️",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CairoFont
                                    )
                                }
                            }
                        }
                    }

                    // Card 2: Active Repo Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C192E))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "المستودع النشط حالياً",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    fontFamily = CairoFont
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "نشط ⚡",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        fontFamily = CairoFont
                                    )
                                }
                            }

                            Text(
                                text = currentActiveRepo?.fullName ?: "Ali-awwww/qabasapp",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "الفرع: ${currentActiveRepo?.defaultBranch ?: "main"}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    fontFamily = CairoFont
                                )
                                Text(
                                    text = if (!currentActiveRepo?.token.isNullOrBlank()) "🔒 مفتاح PAT متصل" else "⚠️ بدون مفتاح PAT",
                                    fontSize = 10.sp,
                                    color = if (!currentActiveRepo?.token.isNullOrBlank()) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    fontFamily = CairoFont
                                )
                            }

                            // Connection Test Button & Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        currentActiveRepo?.let { testConnection(it) }
                                    },
                                    enabled = !isTestingConnection,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1E293B),
                                        contentColor = Color(0xFF38BDF8)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    if (isTestingConnection) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = if (isTestingConnection) "جاري الفحص..." else "اختبار الاتصال ⚡",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CairoFont
                                    )
                                }

                                if (testResult != null) {
                                    Text(
                                        text = testResult ?: "",
                                        fontSize = 9.sp,
                                        color = when (testSuccess) {
                                            true -> Color(0xFF10B981)
                                            false -> Color(0xFFEF4444)
                                            else -> Color(0xFF94A3B8)
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                        fontFamily = CairoFont
                                    )
                                }
                            }
                        }
                    }

                    // Section 3: Saved Repositories List
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "المستودعات المحفوظة للتبديل السريع (${repos.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = CairoFont
                        )
                        TextButton(
                            onClick = { showAddSection = !showAddSection },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (showAddSection) Icons.Default.ExpandLess else Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showAddSection) "إلغاء" else "إضافة مستودع",
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8),
                                fontFamily = CairoFont
                            )
                        }
                    }

                    // Add Repo Form (Collapsible)
                    AnimatedVisibility(visible = showAddSection) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F1B2F))
                                .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "إضافة مستودع جديد للخزانة",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF60A5FA),
                                    fontFamily = CairoFont
                                )

                                OutlinedTextField(
                                    value = inputRepoUrl,
                                    onValueChange = { inputRepoUrl = it },
                                    label = { Text("رابط المستودع أو owner/repo", fontSize = 10.sp) },
                                    placeholder = { Text("مثال: username/my-repo", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF1E2D4A),
                                        focusedLabelColor = Color(0xFF38BDF8),
                                        unfocusedLabelColor = Color(0xFF94A3B8)
                                    )
                                )

                                OutlinedTextField(
                                    value = inputToken,
                                    onValueChange = { inputToken = it },
                                    label = { Text("مفتاح الوصول (GitHub PAT)", fontSize = 10.sp) },
                                    placeholder = { Text("ghp_...", fontSize = 10.sp) },
                                    singleLine = true,
                                    visualTransformation = if (showTokenText) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showTokenText = !showTokenText }) {
                                            Icon(
                                                imageVector = if (showTokenText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF1E2D4A),
                                        focusedLabelColor = Color(0xFF38BDF8),
                                        unfocusedLabelColor = Color(0xFF94A3B8)
                                    )
                                )

                                OutlinedTextField(
                                    value = inputBranch,
                                    onValueChange = { inputBranch = it },
                                    label = { Text("الفرع الافتراضي", fontSize = 10.sp) },
                                    placeholder = { Text("main", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF1E2D4A),
                                        focusedLabelColor = Color(0xFF38BDF8),
                                        unfocusedLabelColor = Color(0xFF94A3B8)
                                    )
                                )

                                Button(
                                    onClick = { handleAddRepo() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2563EB),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                ) {
                                    Text(
                                        text = "حفظ وتعيين كمستودع نشط 💾",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CairoFont
                                    )
                                }
                            }
                        }
                    }

                    // Repositories List Items
                    if (repos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد مستودعات إضافية محفوظة في الخزانة.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontFamily = CairoFont
                            )
                        }
                    } else {
                        repos.forEach { entry ->
                            val isCurrent = entry.id == currentActiveId
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) Color(0xFF0C192E) else Color(0xFF090F1C))
                                    .border(
                                        1.dp,
                                        if (isCurrent) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF1E2D4A),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = entry.fullName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) Color(0xFF10B981) else Color.White,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isCurrent) {
                                                Text(
                                                    text = "● نشط",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF10B981),
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = CairoFont
                                                )
                                            }
                                        }
                                        Text(
                                            text = "فرع: ${entry.defaultBranch} | ${if (entry.token.isNotBlank()) "PAT متوفر" else "بدون PAT"}",
                                            fontSize = 9.sp,
                                            color = Color(0xFF94A3B8),
                                            fontFamily = CairoFont
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (!isCurrent) {
                                            FilledTonalButton(
                                                onClick = { handleSwitchRepo(entry) },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = Color(0xFF1E293B),
                                                    contentColor = Color(0xFF38BDF8)
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(
                                                    text = "تفعيل",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = CairoFont
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { handleDeleteRepo(entry.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "حذف",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color(0xFF050B14)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text(
                        text = "إغلاق وتأكيد الاختيار ✓",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = CairoFont
                    )
                }
            }
        }
    }
}

/**
 * حوار المتطلبات الموحد (Unified Requirements Dialog)
 * يظهر عندما يحاول المستخدم إرسال رسالة دون توفر مفتاح API أو مستودع GitHub.
 */
@Composable
fun AgentRequirementDialog(
    context: Context,
    onDismiss: () -> Unit,
    onConfigComplete: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Auto-detection
    val detectedProvider = remember(apiKey) { UniversalKeyBrain.detect(apiKey) }
    
    // Repository selection
    var showRepoSelector by remember { mutableStateOf(false) }
    var isFetchingRepos by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            color = Color(0xFF0B1220),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Color(0xFFD4AF37).copy(alpha = 0.6f)) // Golden border
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD4AF37).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "متطلبات العمل النشط 🚀",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "لبدء تنفيذ مهامك البرمجية، يحتاج الوكيل إلى مفتاح ذكاء اصطناعي ومستودع GitHub للعمل عليه.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    fontFamily = CairoFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Section 1: AI Engine
                Column(modifier = Modifier.fillMaxWidth()) {
                    RequirementInputSection(
                        title = "1. مفتاح الذكاء الاصطناعي 🧠",
                        description = "سيتم التعرّف على المزود تلقائياً (Gemini, Groq, OpenAI...)",
                        value = apiKey,
                        onValueChange = { 
                            apiKey = it
                            errorMessage = null
                        },
                        placeholder = "AIza... أو Aq... (Gemini), gsk_... (Groq), sk-... (OpenAI)"
                    )
                    
                    if (apiKey.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = detectedProvider.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, detectedProvider.color.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (detectedProvider != DetectedProvider.NONE) Icons.Default.AutoAwesome else Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = detectedProvider.color,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (detectedProvider != DetectedProvider.NONE) 
                                        "المزود المكتشف: ${detectedProvider.displayName}" 
                                        else "لم يتم التعرّف على صيغة المفتاح",
                                    color = detectedProvider.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFont
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Section 2: GitHub Integration
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. مستودع GitHub 🐙",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = CairoFont
                        )
                        
                        val oauthToken = remember { GitHubAuthManager.getAccessToken(context) }
                        if (oauthToken.isNullOrBlank()) {
                            TextButton(
                                onClick = { 
                                    GitHubAuthManager.launchAuth(context)
                                    onDismiss() 
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Login, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ربط الحساب تلقائياً", fontSize = 10.sp, color = Color(0xFF38BDF8), fontFamily = CairoFont)
                            }
                        } else {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                    Text("تم الربط 🐙", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFont)
                                }
                            }
                        }
                    }
                    
                    Text(
                        text = "رابط المستودع (مثال: owner/repo) أو اختر من حسابك بعد الربط",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = CairoFont,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        placeholder = { Text("username/project-name", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFD4AF37),
                            focusedBorderColor = Color(0xFFD4AF37),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        trailingIcon = {
                            val oauthTokenStored = remember { GitHubAuthManager.getAccessToken(context) }
                            if (!oauthTokenStored.isNullOrBlank() || githubToken.isNotBlank()) {
                                IconButton(
                                    onClick = { 
                                        showRepoSelector = true 
                                    },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "جلب المستودعات",
                                        tint = Color(0xFFD4AF37)
                                    )
                                }
                            }
                        }
                    )
                }
                
                if (repoUrl.isBlank()) {
                    val oauthManager = GitHubAuthManager
                    val isOauthConfigured = remember { oauthManager.isConfigured() }
                    
                    if (isOauthConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showRepoSelector = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(18.dp))
                                Text("جلب مستودعاتي تلقائياً 🐙", fontFamily = CairoFont, fontSize = 12.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                RequirementInputSection(
                    title = "رمز GitHub PAT (اختياري)",
                    description = "مطلوب للمستودعات الخاصة ولجلب قائمة المستودعات.",
                    value = githubToken,
                    onValueChange = { githubToken = it },
                    placeholder = "ghp_...",
                    isSecret = true
                )
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Analytics, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "بطاقة تشخيص الخطأ 🔍",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = CairoFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                fontFamily = CairoFont,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { errorMessage = null }) {
                                    Text("تجاهل", color = Color(0xFF94A3B8), fontSize = 12.sp, fontFamily = CairoFont)
                                }
                                Spacer(Modifier.width(12.dp))
                                Button(
                                    onClick = {
                                        errorMessage = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("إعادة المحاولة", color = Color.White, fontSize = 12.sp, fontFamily = CairoFont)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(28.dp))
                
                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text("إلغاء", color = Color.White, fontFamily = CairoFont)
                    }
                    
                    Button(
                        onClick = {
                            if (apiKey.isBlank() || repoUrl.isBlank()) {
                                errorMessage = "يرجى إدخال مفتاح API ورابط المستودع."
                                return@Button
                            }
                            
                            isVerifying = true
                            errorMessage = null
                            
                            scope.launch {
                                try {
                                    // 1. فحص ذكي وحفظ المفتاح
                                    val keyRes = UniversalKeyBrain.saveKeyIfLive(context, apiKey.trim())
                                    if (keyRes !is UniversalKeyBrain.SmartSaveResult.Saved) {
                                        isVerifying = false
                                        errorMessage = when (keyRes) {
                                            is UniversalKeyBrain.SmartSaveResult.Failed -> "فشل فحص المفتاح: ${keyRes.reason}"
                                            is UniversalKeyBrain.SmartSaveResult.Unsupported -> keyRes.reason
                                            else -> "فشل التحقق من المفتاح."
                                        }
                                        return@launch
                                    }
                                    
                                    // 2. فحص GitHub
                                    val effectiveToken = if (githubToken.isNotBlank()) githubToken.trim() else GitHubAuthManager.getAccessToken(context).orEmpty()
                                    val githubHealth = AgentConnectivityEngine.pingGitHubLive(effectiveToken, repoUrl.trim())
                                    if (!githubHealth.isConnected && effectiveToken.isNotBlank()) {
                                        isVerifying = false
                                        errorMessage = "فشل GitHub: ${githubHealth.statusDetail}"
                                        return@launch
                                    }
                                    
                                    // 3. حفظ بيانات المستودع
                                    withContext(Dispatchers.IO) {
                                        val parts = repoUrl.trim().split("/")
                                        if (parts.size >= 2) {
                                            val owner = parts[parts.size - 2]
                                            val repoName = parts[parts.size - 1]
                                            RepoVaultManager.addOrUpdateRepo(
                                                context,
                                                RepoVaultEntry(
                                                    owner = owner,
                                                    repo = repoName,
                                                    token = effectiveToken
                                                )
                                            )
                                        }
                                    }
                                    
                                    isVerifying = false
                                    onConfigComplete()
                                    onDismiss()
                                    
                                } catch (e: Exception) {
                                    isVerifying = false
                                    errorMessage = "حدث خطأ غير متوقع: ${e.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                        enabled = !isVerifying
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0B1220), strokeWidth = 2.dp)
                        } else {
                            Text("حفظ وتفعيل ⚡", color = Color(0xFF0B1220), fontFamily = CairoFont, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showRepoSelector) {
        val oauthToken = GitHubAuthManager.getAccessToken(context)
        val effectiveToken = if (githubToken.isNotBlank()) githubToken else oauthToken.orEmpty()
        
        if (effectiveToken.isBlank()) {
            // إذا لم يكن هناك توكن، نطلب تسجيل الدخول أولاً
            LaunchedEffect(Unit) {
                GitHubAuthManager.startAuth(context)
                showRepoSelector = false
            }
        } else {
            RepositorySelectionDialog(
                token = effectiveToken,
                onRepoSelected = { repo ->
                    repoUrl = repo.fullName
                    showRepoSelector = false
                },
                onDismiss = { showRepoSelector = false }
            )
        }
    }
}

@Composable
fun RepositorySelectionDialog(
    token: String,
    onRepoSelected: (UserRepoSummary) -> Unit,
    onDismiss: () -> Unit
) {
    var repos by remember { mutableStateOf<List<UserRepoSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                repos = GitHubRepoClient.listUserRepos(token)
                isLoading = false
            } catch (e: Exception) {
                error = e.localizedMessage
                isLoading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            color = Color(0xFF0B1220),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Color(0xFFD4AF37).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "اختر مستودعاً 🐙",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontFamily = CairoFont,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFD4AF37))
                    }
                } else if (error != null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "تعذر جلب المستودعات: $error", color = Color.Red, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                } else if (repos.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "لم يتم العثور على مستودعات في هذا الحساب.", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(repos) { repo ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRepoSelected(repo) },
                                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (repo.isPrivate) Color(0xFFF59E0B).copy(alpha = 0.1f) else Color(0xFF10B981).copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                            contentDescription = null,
                                            tint = if (repo.isPrivate) Color(0xFFF59E0B) else Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = repo.fullName,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = CairoFont
                                        )
                                        if (repo.description.isNotBlank()) {
                                            Text(
                                                text = repo.description,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontFamily = CairoFont
                                            )
                                        }
                                        Text(
                                            text = "تحديث: ${repo.updatedAt.take(10)}",
                                            color = Color(0xFF64748B),
                                            fontSize = 9.sp,
                                            fontFamily = CairoFont
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text("إغلاق", color = Color.White, fontFamily = CairoFont)
                }
            }
        }
    }
}

@Composable
private fun RequirementInputSection(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isSecret: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = CairoFont,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontFamily = CairoFont,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 12.sp, color = Color(0xFF475569)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFD4AF37),
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A)
            ),
            singleLine = true
        )
    }
}
