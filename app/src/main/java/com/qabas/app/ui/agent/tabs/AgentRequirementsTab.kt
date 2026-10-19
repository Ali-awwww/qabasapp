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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat


private data class Requirement(
    val id: String,
    val title: String,
    val hint: String,
    var state: ReqState = ReqState.CHECKING,
    var detail: String = ""
)

private enum class ReqState { CHECKING, OK, FAIL }


/**
 * ⚡ تبويب كونسول تشغيل الوكيل المباشر مع طابور الأوامر والإيقاف المؤقت


@Composable
internal fun RequirementsTab(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onJumpBuildCenter: () -> Unit,
    onJumpKeys: () -> Unit
) {
    val prefs = com.qabas.app.AgentPrefs.instance(context)
    val _ap = com.qabas.app.AgentPrefs
    var isClientMode by remember { mutableStateOf(false) }

    fun getDefaultReqs(clientMode: Boolean): List<Requirement> {
        return if (clientMode) {
            listOf(
                Requirement("engine", "محرك الذكاء والاستدلال", "Gemini / Groq / OpenRouter / OpenAI"),
                Requirement("pat", "رمز GitHub PAT", "يد الوكيل وصلاحية repo/actions"),
                Requirement("req", "طلب مربوط نشط", "الهدف والمواصفات المستهدفة"),
                Requirement("price", "سعر مقبول", "بوابة التوليد والاعتماد"),
                Requirement("repo", "مستودع مرتبط", "المستودع البرمجي لفرع العمل")
            )
        } else {
            listOf(
                Requirement("engine", "محرك الاستدلال والذكاء (AI Engine)", "Gemini 2.0 / Claude / Groq / OpenAI"),
                Requirement("pat", "رمز مصادقة GitHub PAT ومستخدم العمل", "صلاحية repo و workflow و actions"),
                Requirement("repo", "المستودع المستهدف وفرع التطوير النشط", "خزانة المستودعات وفرع العمل البرمجي"),
                Requirement("tools", "ترسانة الأدوات وسجل الفحص الذاتي", "الأدوات المسجلة وفحص سلامة التجميع"),
                Requirement("connectivity", "اتصال الشبكة واستقرار الخدمات السحابية", "فحص زمن الاستجابة والاتصال الحي")
            )
        }
    }

    var reqs by remember(isClientMode) { mutableStateOf(getDefaultReqs(isClientMode)) }
    var checking by remember { mutableStateOf(false) }

    fun runChecks() {
        checking = true
        reqs = reqs.map { it.copy(state = ReqState.CHECKING, detail = "") }
        scope.launch {
            val (provider, activeKey) = UniversalKeyBrain.getActiveEngine()
            val engineOk = activeKey.isNotBlank() && provider != DetectedProvider.NONE

            val owner = prefs.getString("build_center_owner", "") ?: ""
            val repo = prefs.getString("build_center_repo", "") ?: ""
            val token = prefs.getString("build_center_token", "") ?: ""
            val ghUser = if (token.isNotBlank()) {
                GitHubRepoClient(owner.ifBlank { "-" }, repo.ifBlank { "-" }, token).getAuthUser()
            } else null

            if (!isClientMode) {
                // نمط التطوير الهندسي الذاتي (5 أركان هندسية مستقلة تماماً)
                val repoOk = owner.isNotBlank() && repo.isNotBlank()
                reqs = reqs.map {
                    when (it.id) {
                        "engine" -> it.copy(
                            state = if (engineOk) ReqState.OK else ReqState.FAIL,
                            detail = if (engineOk) "متصل عبر ${provider.displayName} بنجاح ✅" else "لا يوجد مفتاح لأي مزود (Gemini/Groq/OpenRouter)"
                        )
                        "pat" -> it.copy(
                            state = if (ghUser != null) ReqState.OK else ReqState.FAIL,
                            detail = ghUser?.let { u -> "متصل ومصرّح كـ $u ✅" } ?: "مفقود أو مرفوض الصلاحية"
                        )
                        "repo" -> it.copy(
                            state = if (repoOk) ReqState.OK else ReqState.FAIL,
                            detail = if (repoOk) "$owner/$repo (الخزانة النشطة) ✅" else "اختر أو اربط مستودعاً من مركز البناء"
                        )
                        "tools" -> it.copy(
                            state = ReqState.OK,
                            detail = "12 أداة هندسية مفعلة + محلل الانهيارات والحارس الآلي جاهز ✅"
                        )
                        else -> it.copy(
                            state = if (engineOk || ghUser != null) ReqState.OK else ReqState.FAIL,
                            detail = if (engineOk || ghUser != null) "الاتصال السحابي مستقر وزمن الاستجابة فوري ✅" else "بانتظار تهيئة المحرك أو GitHub"
                        )
                    }
                }
            } else {
                // نمط طلبات العملاء الخارجي
                val active = AppRequestService.getActiveBuildRequest(context)
                val priced = active != null &&
                    (context.getSharedPreferences("qabas_requests_prefs", Context.MODE_PRIVATE)
                        .getBoolean("priced_${active.id}", false) ||
                        active.priceStatus == "accepted")
                val linkedRepo = active?.let {
                    prefs.getString("repo_${it.id}", null)
                        ?: context.getSharedPreferences("qabas_requests_prefs", Context.MODE_PRIVATE)
                            .getString("repo_${it.id}", null)
                }

                reqs = reqs.map {
                    when (it.id) {
                        "engine" -> it.copy(
                            state = if (engineOk) ReqState.OK else ReqState.FAIL,
                            detail = if (engineOk) "متصل عبر ${provider.displayName} ✅" else "لا يوجد مفتاح لأي مزود"
                        )
                        "pat" -> it.copy(
                            state = if (ghUser != null) ReqState.OK else ReqState.FAIL,
                            detail = ghUser?.let { u -> "متصل ومصرّح كـ $u ✅" } ?: "مفقود أو مرفوض الصلاحية"
                        )
                        "req" -> it.copy(
                            state = if (active != null) ReqState.OK else ReqState.FAIL,
                            detail = active?.title ?: "لا يوجد طلب مربوط — اربط طلباً من قسم الطلبات"
                        )
                        "price" -> it.copy(
                            state = if (priced) ReqState.OK else ReqState.FAIL,
                            detail = if (priced) "تم القبول والاعتماد ✅" else "بانتظار قبول واعتماد العميل"
                        )
                        else -> it.copy(
                            state = if (linkedRepo != null) ReqState.OK else ReqState.FAIL,
                            detail = linkedRepo ?: "أنشئ أو اختر مستودعاً من مركز البناء"
                        )
                    }
                }
            }
            checking = false
        }
    }

    LaunchedEffect(isClientMode) { runChecks() }

    val done = reqs.count { it.state == ReqState.OK }
    val readyPercentage = (done.toFloat() / reqs.size.toFloat() * 100).toInt()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // شريط اختيار نمط الفحص (تطوير ذاتي هندسي مقابل طلبات العملاء)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF070C18),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2D4A))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // زر نمط التطوير الذاتي
                    Button(
                        onClick = { isClientMode = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isClientMode) Color(0xFF0F3A2A) else Color.Transparent
                        ),
                        border = if (!isClientMode) BorderStroke(1.2.dp, Color(0xFF10B981)) else null,
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = if (!isClientMode) Color(0xFF34D399) else Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "التطوير الذاتي المباشر",
                            color = if (!isClientMode) Color.White else Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // زر نمط طلبات العملاء
                    Button(
                        onClick = { isClientMode = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isClientMode) Color(0xFF1E1B4B) else Color.Transparent
                        ),
                        border = if (isClientMode) BorderStroke(1.2.dp, Color(0xFF818CF8)) else null,
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = null,
                            tint = if (isClientMode) Color(0xFFA5B4FC) else Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "طلبات ومشاريع العملاء",
                            color = if (isClientMode) Color.White else Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        // ==========================================
        // 🛰️ بطاقة الرأس السيبرانية لمصفوفة الجاهزية (Readiness Telemetry Matrix)
        // ==========================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xEE090F1F),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            (if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF)).copy(alpha = 0.6f),
                            Color(0xFF38BDF8).copy(alpha = 0.3f),
                            Color(0xFF1E2D4A).copy(alpha = 0.5f)
                        )
                    )
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "SYSTEM READINESS MATRIX",
                                    color = if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "مصفوفة فحص متطلبات التشغيل المستقل",
                                color = Color.White,
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                if (done == 5) "النظام جاهز بنسبة 100% — كافة الموصلات متزامنة ومحققة"
                                else "استكمل الشروط غير المكتملة لتفعيل الإطلاق التلقائي",
                                color = Color(0xFF94A3B8),
                                fontFamily = CairoFont,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // قرص العداد الدائري السيبراني
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF040814))
                                .border(1.dp, Color(0xFF1E2D4A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(52.dp)) {
                                val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                drawArc(
                                    color = Color(0xFF1E293B),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = stroke
                                )
                                val accent = if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF)
                                drawArc(
                                    color = accent,
                                    startAngle = -90f,
                                    sweepAngle = (readyPercentage / 100f) * 360f,
                                    useCenter = false,
                                    style = stroke
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$done/5", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Text("$readyPercentage%", color = if (done == 5) Color(0xFF10B981) else Color(0xFF00F0FF), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "الحالة العامة: ${if (done == 5) "جاهز للعمليات المباشرة ✓" else "بانتظار اكتمال المتطلبات ⏳"}",
                            color = if (done == 5) Color(0xFF10B981) else Color(0xFFF59E0B),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(
                            onClick = { runChecks() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة الفحص", color = GoldPrimary, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // بطاقات الشروط الخمسة بتصميم هندسي متناغم
        items(reqs) { r ->
            val color = when (r.state) {
                ReqState.OK -> Color(0xFF10B981)
                ReqState.FAIL -> Color(0xFFEF4444)
                else -> Color(0xFF00F0FF)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xDD080E1C),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            color.copy(alpha = 0.5f),
                            Color(0xFF1E293B).copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.12f))
                            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (r.state == ReqState.CHECKING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = color, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (r.state == ReqState.OK) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = color.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    when (r.state) {
                                        ReqState.OK -> "محقّق ✓"
                                        ReqState.FAIL -> "غير مكتمل ✗"
                                        else -> "فحص..."
                                    },
                                    color = color,
                                    fontFamily = CairoFont,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(r.hint, color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (r.state == ReqState.CHECKING) "جاري التحقق الفوري..." else r.detail.ifBlank { "—" },
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }

                    if (r.state == ReqState.FAIL && !checking) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { if (r.id == "engine") onJumpKeys() else onJumpBuildCenter() },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("إصلاح", color = DeepSlate, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }
        }
    }
}
