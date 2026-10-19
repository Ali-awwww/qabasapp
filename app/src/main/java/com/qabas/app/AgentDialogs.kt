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
 * حوار مستودع GitHub والفرع المستهدف
 */
@Composable
fun RepoSetupDialog(
    context: Context,
    activeRepo: RepoVaultEntry?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassmorphicSurface(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF0F172A),
            borderColor = Color(0xFFE5B842).copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🐙 مستودع GitHub المتصل بالوكيل",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "المستودع النشط: ${activeRepo?.fullName ?: "Ali-awwww/qabasapp"}",
                    fontSize = 12.sp,
                    color = Color(0xFFE5B842),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "الفرع الافتراضي: ${activeRepo?.defaultBranch ?: "main"}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "يتم إرسال أوامر الوكيل والبناء السحابي عبر GitHub Actions مباشرة إلى هذا المستودع.",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5B842), contentColor = Color(0xFF070B14)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق وتأكيد", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
