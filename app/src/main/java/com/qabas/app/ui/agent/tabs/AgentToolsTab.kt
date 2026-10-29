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


/**
 * ⚙️ بطاقة إعدادات نواة الوكيل: النموذج المستهدف، حد الجولات، والإصلاح الذاتي.
 */
@Composable
internal fun AgentKernelSettingsCard(context: Context) {
    val prefs = com.qabas.app.AgentPrefs.instance(context)
    var model by remember { mutableStateOf(AgentPrefs.model(context)) }
    var maxTurns by remember { mutableStateOf(AgentPrefs.maxTurns(context).toFloat()) }
    var autoFix by remember { mutableStateOf(AgentPrefs.isAutoFix(context)) }

    Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xEE090F1F),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        GoldPrimary.copy(alpha = 0.6f),
                        Color(0xFF00F0FF).copy(alpha = 0.3f),
                        Color(0xFF1E2D4A).copy(alpha = 0.5f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AGENT REASONING PARAMETERS",
                            color = GoldPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            "تهيئة النواة ⚙️",
                            color = GoldPrimary,
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text("نموذج الاستدلال والبرمجة المستهدف:", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        null to "⚡ تلقائي الذكي (Auto)",
                        "gemini-3.8-flash" to "💎 Gemini 3.8 Flash (سريع ودقيق)",
                        "llama-3.3-70b-versatile" to "⚡ Groq Llama 3.3 70B (فائق السرعة)",
                        "google/gemma-4-31b-it:free" to "🌊 Gemma 4 Free (مجاني ومفتوح)"
                    ).forEach { (id, label) ->
                        val isSelected = model == id
                        Surface(
                            color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFF091220),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFF1E2D4A)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    model = id
                                    prefs.edit().putString(AgentPrefs.MODEL, id ?: "").apply()
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontFamily = CairoFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.6f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("الحد الأقصى لجولات التفكير (Max Turns):", color = Color.White, fontFamily = CairoFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("عدد الجولات المسموح بها للوكيل لإكمال المهمة", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
                    }
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Text(
                            "${maxTurns.toInt()} جولات",
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Slider(
                    value = maxTurns,
                    onValueChange = {
                        maxTurns = it
                        prefs.edit().putInt(AgentPrefs.MAX_TURNS, it.toInt()).apply()
                    },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF10B981),
                        activeTrackColor = Color(0xFF10B981),
                        inactiveTrackColor = Color(0xFF1E2D4A)
                    )
                )

                HorizontalDivider(color = Color(0xFF1E2D4A).copy(alpha = 0.6f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔧 الإصلاح التلقائي الذكي (Self-Healing)", color = Color.White, fontFamily = CairoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (autoFix) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF64748B).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    if (autoFix) "ACTIVE" else "OFF",
                                    color = if (autoFix) Color(0xFF10B981) else Color(0xFF64748B),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text("عند حدوث خطأ تجميع، يحلل الوكيل الخطأ ويعيد بناء الكود تلقائياً.", color = Color(0xFF94A3B8), fontFamily = CairoFont, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoFix,
                        onCheckedChange = {
                            autoFix = it
                            prefs.edit().putBoolean(AgentPrefs.AUTO_FIX, it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF10B981),
                            checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF1E2D4A)
                        )
                    )
                }
            }
        }
}

@Composable
internal fun ToolsTab(context: Context) {
    val prefs = com.qabas.app.AgentPrefs.instance(context)
    val dummyCtx = remember { CodeTools.Ctx("-", "-", "-", "-") }
    val allTools = remember { CodeTools.definitions(dummyCtx) + AiTools.definitions(context) }
    var states by remember { mutableStateOf(allTools.associate { it.name to AgentPrefs.isToolEnabled(context, it.name) }) }

    val arabicNames = mapOf(
        "list_tree" to "📁 تصفح شجرة المستودع",
        "read_file" to "📖 قراءة محتوى ملف",
        "write_file" to "✏️ إنشاء وكتابة ملف جديد",
        "update_file" to "🔄 ترقيع وتحديث ملف موجود",
        "delete_file" to "🗑️ حذف ملف من الفرع",
        "list_branches" to "🌿 قائمة فروع Git",
        "recent_commits" to "📜 سجل الـ Commits الأخيرة",
        "trigger_build" to "🏗️ تشغيل بناء CI/CD في السحابة",
        "build_status" to "📊 متابعة حالة البناء المباشر",
        "create_pr" to "🔀 فتح Pull Request رسمي",
        "list_prs" to "📥 السحوبات المفتوحة",
        "pr_files" to "🔍 فحص ملفات السحب",
        "merge_pr" to "✅ دمج السحب تلقائياً",
        "create_issue" to "🐞 تسجيل مشكلة برمجية (Issue)",
        "list_issues" to "📋 قائمة القضايا المفتوحة",
        "device_status" to "📱 فحص صحة الجهاز",
        "diagnose" to "🩺 التشخيص الهندسي الشامل",
        "generate_script" to "🎬 توليد سيناريو دعوي",
        "search_quran" to "🔍 محرك البحث القرآني",
        "get_tafsir" to "📖 استرجاع التفسير الموثق",
        "search_code_symbols" to "🔍 البحث الدلالي عن الدوال والكلاسات",
        "diagnose_build_error" to "🩺 تشخيص أسباب أخطاء التجميع",
        "run_linter_check" to "🧪 التدقيق النحوي والساكن (Linting)",
        "generate_unit_test" to "🧪 توليد اختبارات الوحدات الآلية (Unit Tests)",
        "refactor_and_optimize" to "⚡ فحص وتحسين أداء الذاكرة و Compose"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ترسانة الأدوات فقط — إعدادات النواة انتقلت إلى صفحة الإعدادات
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ترسانة الأدوات البرمجية المتاحة (${allTools.size} أداة):",
                    color = Color.White,
                    fontFamily = CairoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    "${states.values.count { it }} مفعّلة",
                    color = Color(0xFF10B981),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(allTools) { tool ->
            val on = states[tool.name] == true
            Surface(
                color = Color(0xDD080E1C),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (on) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF1E2D4A).copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF1E293B).copy(alpha = 0.3f))
                            .border(1.dp, if (on) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF1E2D4A), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (tool.name.contains("file")) Icons.Default.Description
                            else if (tool.name.contains("pr") || tool.name.contains("branch")) Icons.Default.AltRoute
                            else if (tool.name.contains("build")) Icons.Default.Build
                            else if (tool.name.contains("test")) Icons.Default.Science
                            else Icons.Default.Code,
                            contentDescription = null,
                            tint = if (on) Color(0xFF10B981) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                arabicNames[tool.name] ?: tool.name,
                                color = if (on) Color.White else Color(0xFF64748B),
                                fontFamily = CairoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                tool.name,
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            tool.description,
                            color = Color(0xFF94A3B8),
                            fontFamily = CairoFont,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }

                    Switch(
                        checked = on,
                        onCheckedChange = {
                            states = states + (tool.name to it)
                            prefs.edit().putBoolean(AgentPrefs.toolKey(tool.name), it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF10B981),
                            checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF1E2D4A)
                        )
                    )
                }
            }
        }
    }
}
